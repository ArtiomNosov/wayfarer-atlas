package org.wayfarer.atlas.data;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.time.LocalDate;

/** Select source metadata before parsing the large rule array. */
public final class SnapshotSelection {
    private SnapshotSelection() {}
    public static boolean preferCache(boolean cacheExists, String cacheDate, String bundledDate) {
        if (!cacheExists || cacheDate == null) return false;
        return bundledDate == null || cacheDate.compareTo(bundledDate) >= 0;
    }
    public static String publicationDate(String json) {
        if (json == null) return null;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("updatedAt")) {
                    if (reader.peek() == JsonToken.NULL) return null;
                    String date = reader.nextString();
                    if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
                    LocalDate.parse(date);
                    return date;
                }
                reader.skipValue();
            }
        } catch (Exception ignored) { return null; }
        return null;
    }
}
