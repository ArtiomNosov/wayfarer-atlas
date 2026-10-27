package org.wayfarer.atlas.data;

import java.util.Set;
import java.net.URI;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Adapter for the public VisaChecker compilation, not an official border service. */
public final class CommunityMatrixParser {
    public static final String SOURCE_URL = "https://github.com/maxix7/visa-requirements-dataset";
    private CommunityMatrixParser() {}
    public static String normalize(String json, Set<String> codes) {
        return normalizeAndParse(json, codes).getCanonical();
    }
    public static NormalizedReference normalizeAndParse(String json, Set<String> codes) {
        try {
            if (json == null || json.length() > 16 * 1024 * 1024) throw invalid();
            JsonObject source = JsonParser.parseString(json).getAsJsonObject();
            if (!"visa-requirements".equals(text(source, "name", true))
                    || !"CC-BY-4.0".equals(text(source, "license", true))) throw invalid();
            JsonObject matrix = source.getAsJsonObject("rules");
            if (matrix == null || matrix.size() > 300) throw invalid();
            JsonObject normalized = new JsonObject();
            normalized.addProperty("version", 1);
            normalized.addProperty("source", SOURCE_URL);
            normalized.addProperty("updatedAt", text(source, "updated", false));
            JsonArray rows = new JsonArray();
            for (var origin : matrix.entrySet()) {
                if (!codes.contains(origin.getKey())) throw invalid();
                JsonObject destinations = origin.getValue().getAsJsonObject();
                for (var destination : destinations.entrySet()) {
                    if (!codes.contains(destination.getKey())) throw invalid();
                    if (origin.getKey().equals(destination.getKey())) continue;
                    if (rows.size() >= 100000) throw invalid();
                    JsonObject raw = destination.getValue().getAsJsonObject();
                    JsonObject row = new JsonObject();
                    row.addProperty("citizenship", origin.getKey());
                    row.addProperty("destination", destination.getKey());
                    row.addProperty("category", category(text(raw, "requirement", true)));
                    JsonElement duration = raw.get("max_stay_days");
                    if (duration != null && !duration.isJsonNull()
                            && (!duration.isJsonPrimitive() || !duration.getAsJsonPrimitive().isNumber())) throw invalid();
                    row.add("maxStayDays", duration);
                    String verified = text(raw, "verified", false);
                    row.addProperty("checkedAt", verified);
                    String notes = "Community reference; ordinary passport tourism; check official conditions.";
                    if (verified == null) notes += " Not individually verified.";
                    String primary = text(raw, "source", false);
                    if (primary != null && primary.length() > 2048) throw invalid();
                    String reference = SOURCE_URL;
                    if (primary != null && isHttps(primary)) reference = primary;
                    else if (primary != null) notes += " Upstream reference: " + primary;
                    row.addProperty("sourceUrl", reference);
                    row.addProperty("notes", notes);
                    rows.add(row);
                }
            }
            normalized.add("rules", rows);
            String canonical = normalized.toString();
            // Reuse all country/date/duration/source validation before caching an update.
            return new NormalizedReference(canonical, DatasetParser.parseRules(canonical, codes));
        } catch (RuntimeException failure) { throw invalid(); }
    }
    public static RuleSnapshot parse(String json, Set<String> codes) { return normalizeAndParse(json, codes).getSnapshot(); }
    public static final class NormalizedReference {
        private final String canonical;
        private final RuleSnapshot snapshot;
        private NormalizedReference(String canonical, RuleSnapshot snapshot) { this.canonical = canonical; this.snapshot = snapshot; }
        public String getCanonical() { return canonical; }
        public RuleSnapshot getSnapshot() { return snapshot; }
    }
    private static String text(JsonObject object, String key, boolean required) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) throw invalid();
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid();
        String result = value.getAsString();
        if (result.length() > 2048 || (required && result.trim().isEmpty())) throw invalid();
        return result;
    }
    private static boolean isHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static String category(String requirement) {
        switch (requirement) {
            case "visa_free": return "VISA_FREE";
            case "visa_on_arrival": return "VISA_ON_ARRIVAL";
            case "eta": return "ETA";
            case "evisa": return "E_VISA";
            case "visa_required": return "VISA_REQUIRED";
            case "no_admission": return "RESTRICTED";
            default: return "UNKNOWN";
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid community reference dataset"); }
}
