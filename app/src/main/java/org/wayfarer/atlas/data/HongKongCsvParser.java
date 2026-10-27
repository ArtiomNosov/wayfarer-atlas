package org.wayfarer.atlas.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Official open-data CSV adapter. Changed document types fail closed until the mapping is reviewed. */
public final class HongKongCsvParser {
    public static final String SOURCE_URL = "https://www.immd.gov.hk/eng/services/visas/visit-transit/visit-visa-entry-permit.html";
    private static final List<String> HEADER = Arrays.asList(
        "National of foreign country (territory)/Type of travel document",
        "Visa free period for visiting the Hong Kong Special Administrative Region", "Visa requirements");
    private static final String CONDITIONS_EN = "Ordinary short visit only. Adequate funds and an onward/return ticket are required, subject to official exceptions. No work, study or residence. Entry remains at the immigration authority's discretion.";
    private static final String CONDITIONS_RU = "Только краткосрочное посещение по обычному загранпаспорту. Нужны достаточные средства и билет для выезда или возвращения с учётом официальных исключений. Работа, учёба и проживание не разрешены. Решение о допуске принимает иммиграционная служба.";
    private HongKongCsvParser() {}

    public static String normalize(String csv, String mapping, Set<String> codes, String checkedAt) {
        try {
            java.time.LocalDate.parse(checkedAt);
            JsonObject config = JsonParser.parseString(mapping).getAsJsonObject();
            if (config.get("version").getAsInt() != 1 || !"HK".equals(config.get("destination").getAsString())) throw invalid();
            List<List<String>> table = table(csv);
            if (table.isEmpty() || !table.get(0).equals(HEADER)) throw invalid();
            String sourceAsAt = null;
            List<String> footer = table.get(table.size() - 1);
            if (!footer.isEmpty() && footer.stream().skip(1).allMatch(String::isEmpty)
                    && footer.get(0).startsWith("(As at ") && footer.get(0).endsWith(")")) {
                String value = footer.get(0).substring(7, footer.get(0).length() - 1);
                sourceAsAt = java.time.LocalDate.parse(value,
                        java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)
                                .withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString();
                table.remove(table.size() - 1);
            }
            Map<String, Requirement> rows = new LinkedHashMap<>();
            for (int index = 1; index < table.size(); index++) {
                List<String> row = table.get(index);
                if (row.size() != 3 || row.get(0).isEmpty() || rows.containsKey(row.get(0))) throw invalid();
                rows.put(row.get(0), requirement(row.get(1), row.get(2)));
            }
            Set<String> expectedLabels = new HashSet<>();
            for (JsonElement label : config.getAsJsonArray("sourceLabels")) if (!expectedLabels.add(label.getAsString())) throw invalid();
            if (!rows.keySet().equals(expectedLabels)) throw invalid();
            JsonObject normalized = new JsonObject();
            normalized.addProperty("version", "1"); normalized.addProperty("source", SOURCE_URL);
            normalized.addProperty("updatedAt", checkedAt);
            normalized.addProperty("sourceAsAt", sourceAsAt);
            JsonArray rules = new JsonArray(); Set<String> origins = new HashSet<>();
            for (JsonElement item : config.getAsJsonArray("entries")) {
                JsonObject entry = item.getAsJsonObject(); String code = entry.get("code").getAsString();
                if (!codes.contains(code) || "HK".equals(code) || !origins.add(code)) throw invalid();
                Requirement selected = null; boolean conflict = false;
                JsonArray labels = entry.getAsJsonArray("rows"); if (labels.isEmpty()) throw invalid();
                for (JsonElement label : labels) {
                    Requirement candidate = rows.get(label.getAsString()); if (candidate == null) throw invalid();
                    if (selected == null) selected = candidate;
                    else if (!selected.category.equals(candidate.category) || !Objects.equals(selected.days, candidate.days)) conflict = true;
                }
                String override = optional(entry, "categoryOverride");
                if (override != null && !override.equals("UNKNOWN") && !override.equals("ETA")) throw invalid();
                if (conflict || "UNKNOWN".equals(override)) selected = new Requirement("UNKNOWN", null);
                JsonObject rule = new JsonObject();
                rule.addProperty("citizenship", code); rule.addProperty("destination", "HK");
                rule.addProperty("category", selected.category); rule.addProperty("maxStayDays", selected.days);
                String notes = optional(entry, "notesRuntime"), notesRu = optional(entry, "notesRuntimeRu");
                if ("UNKNOWN".equals(override)) {
                    notes = "Requirements depend on the precise passport subtype, which is not selected in this app. Check the official table.";
                    notesRu = "Условия зависят от точного подтипа паспорта, который не выбирается в приложении. Сверьтесь с официальной таблицей.";
                } else if ("ETA".equals(override) && !"ETA".equals(selected.category)) {
                    notes = "The current requirement differs from the reviewed pre-arrival registration arrangement. Check the official source.";
                    notesRu = "Текущие условия отличаются от ранее проверенной предварительной регистрации. Уточните их в официальном источнике.";
                }
                rule.addProperty("notes", CONDITIONS_EN + (notes == null ? "" : " " + notes));
                rule.addProperty("notesRu", CONDITIONS_RU + (notesRu == null ? "" : " " + notesRu));
                rule.addProperty("sourceUrl", SOURCE_URL); rule.addProperty("checkedAt", checkedAt);
                rules.add(rule);
            }
            normalized.add("rules", rules);
            return normalized.toString();
        } catch (RuntimeException error) { throw invalid(); }
    }

    private static Requirement requirement(String daysText, String visaText) {
        String visa = visaText.toLowerCase(Locale.ROOT).trim();
        Integer days = daysText.matches("[1-9][0-9]{0,3}") ? Integer.valueOf(daysText) : null;
        if (days != null && days > 3660) days = null;
        if (days != null && visa.contains("pre-arrival registration")) return new Requirement("ETA", days);
        if (days != null && visa.equals("not applicable")) return new Requirement("VISA_FREE", days);
        if (days == null && visa.contains("required to hold a valid visa")) return new Requirement("VISA_REQUIRED", null);
        return new Requirement("UNKNOWN", null);
    }
    private static String optional(JsonObject object, String key) {
        JsonElement value = object.get(key); return value == null || value.isJsonNull() ? null : value.getAsString();
    }
    private static final class Requirement {
        final String category; final Integer days;
        Requirement(String category, Integer days) { this.category = category; this.days = days; }
    }

    private static List<List<String>> table(String csv) {
        if (csv == null || csv.length() > 2 * 1024 * 1024) throw invalid();
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder(); boolean quoted = false, closed = false;
        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') { cell.append('"'); index++; }
                    else { quoted = false; closed = true; }
                } else cell.append(character);
            } else if (character == ',' || character == '\n' || character == '\r') {
                row.add(cell.toString().trim()); cell.setLength(0); closed = false;
                if (character != ',') {
                    if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                    row = new ArrayList<>();
                    if (character == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') index++;
                }
            } else if (character == '"') {
                if (cell.length() != 0 || closed) throw invalid(); quoted = true;
            } else {
                if (closed) throw invalid(); cell.append(character);
            }
            if (rows.size() > 1000 || cell.length() > 20000 || row.size() > 3) throw invalid();
        }
        if (quoted) throw invalid();
        if (cell.length() != 0 || closed || !row.isEmpty()) { row.add(cell.toString().trim()); rows.add(row); }
        return rows;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid official Hong Kong reference"); }
}
