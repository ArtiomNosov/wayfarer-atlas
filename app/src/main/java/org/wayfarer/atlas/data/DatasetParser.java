package org.wayfarer.atlas.data;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.net.URI;
import java.time.LocalDate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.wayfarer.atlas.domain.Country;
import org.wayfarer.atlas.domain.EntryCategory;
import org.wayfarer.atlas.domain.EntryRule;
public final class DatasetParser {
    private DatasetParser() {}
    public static List<Country> parseCountries(String json) {
        try {
            JsonArray rows = object(json).getAsJsonArray("countries");
            if (rows == null || rows.isEmpty() || rows.size() > 300) throw invalid();
            List<Country> countries = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonElement row : rows) {
                JsonObject value = row.getAsJsonObject();
                String code = text(value, "code", 2, true);
                if (!code.matches("[A-Z]{2}") || !seen.add(code)) throw invalid();
                countries.add(new Country(code, text(value, "nameEn", 160, true), text(value, "nameRu", 160, true)));
            }
            return countries;
        } catch (RuntimeException error) { throw invalid(); }
    }

    public static RuleSnapshot parseRules(String json, Set<String> codes) {
        try {
            JsonObject root = object(json);
            if (!"1".equals(root.get("version").getAsString())) throw invalid();
            String source = text(root, "source", 2048, false);
            if (source != null) source = url(source);
            String updatedAt = date(text(root, "updatedAt", 10, false));
            JsonArray rows = root.getAsJsonArray("rules");
            if (rows == null || rows.size() > 100000) throw invalid();
            if (source == null && !rows.isEmpty()) throw invalid();
            List<EntryRule> rules = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonElement row : rows) {
                JsonObject value = row.getAsJsonObject();
                String citizenship = text(value, "citizenship", 2, true);
                String destination = text(value, "destination", 2, true);
                if (!codes.contains(citizenship) || !codes.contains(destination) || citizenship.equals(destination)
                        || !seen.add(citizenship + ":" + destination)) throw invalid();
                EntryCategory category = EntryCategory.valueOf(text(value, "category", 40, true));
                Integer days = null;
                if (value.has("maxStayDays") && !value.get("maxStayDays").isJsonNull()) {
                    String input = value.get("maxStayDays").getAsString();
                    if (!input.matches("[1-9][0-9]{0,4}")) throw invalid();
                    days = Integer.parseInt(input);
                    if (days > 3660) throw invalid();
                }
                String notes = text(value, "notes", 10000, false);
                String notesRu = text(value, "notesRu", 10000, false);
                String ruleSource = text(value, "sourceUrl", 2048, false);
                if (ruleSource != null) ruleSource = url(ruleSource);
                rules.add(new EntryRule(citizenship, destination, category, days, notes == null ? "" : notes,
                        ruleSource, date(text(value, "checkedAt", 10, false)), notesRu,
                        ruleSource == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(
                                new org.wayfarer.atlas.domain.RuleCitation("", "", ruleSource, date(text(value, "checkedAt", 10, false)), false))));
            }
            return new RuleSnapshot(rules, source, updatedAt, date(text(root, "sourceAsAt", 10, false)));
        } catch (RuntimeException error) { throw invalid(); }
    }

    private static JsonObject object(String json) {
        if (json == null || json.length() > 16 * 1024 * 1024) throw invalid();
        return JsonParser.parseString(json).getAsJsonObject();
    }
    private static String text(JsonObject value, String key, int limit, boolean required) {
        JsonElement element = value.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) throw invalid();
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw invalid();
        String result = element.getAsString();
        if (result.length() > limit || (required && result.trim().isEmpty())) throw invalid();
        return result;
    }
    private static String date(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        LocalDate.parse(value);
        return value;
    }
    private static String url(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) throw invalid();
        return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid reference dataset"); }
}
