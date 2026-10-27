package org.wayfarer.atlas.storage;

import org.wayfarer.atlas.domain.Trip;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Local-only journal. No Java object deserialization and no logging of records. */
public final class TripStore {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RECORDS = 5000;
    private static final String VERSION = "1";
    private final File directory;
    private final File file;

    public TripStore(File directory) {
        if (directory == null) throw new IllegalArgumentException("Storage directory is required.");
        this.directory = directory;
        this.file = new File(directory, "trips.properties");
    }

    public synchronized List<Trip> load() throws IOException {
        checkDirectory();
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return new ArrayList<>();
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Trip storage is not a regular file.");
        }
        if (Files.size(file.toPath()) > MAX_BYTES) throw new IOException("Trip file exceeds the 2 MiB limit.");

        byte[] data;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw new IOException("Trip file exceeds the 2 MiB limit.");
                bytes.write(buffer, 0, read);
            }
            data = bytes.toByteArray();
        }

        Properties properties = new StrictProperties();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(data),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            properties.load(reader);
        } catch (IllegalArgumentException error) {
            throw new IOException("Trip file contains malformed or duplicate fields.");
        }
        return decode(properties);
    }

    public synchronized void save(List<Trip> trips) throws IOException {
        // Refuse to erase an unreadable original, even if the caller did not load it first.
        load();
        if (trips == null) throw new IOException("Trips are required.");
        List<Trip> snapshot;
        try { snapshot = new ArrayList<>(trips); }
        catch (RuntimeException error) { throw new IOException("Trip list could not be read safely."); }
        if (snapshot.size() > MAX_RECORDS) throw new IOException("Trip count exceeds the storage limit.");

        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("count", Integer.toString(snapshot.size()));
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < snapshot.size(); i++) {
            Trip trip = snapshot.get(i);
            if (trip == null) throw new IOException("Trip list contains an empty record.");
            if (!ids.add(trip.getId())) throw new IOException("Trip list contains duplicate identifiers.");
            String key = UUID.randomUUID().toString();
            properties.setProperty("record." + i, key);
            String prefix = "trip." + key + ".";
            try {
                // Validate every record before creating or replacing any file.
                new Trip(trip.getId(), trip.getDestination(), trip.getStartDate().toString(),
                        trip.getEndDate().toString(), trip.getNote(), trip.isReminderEnabled());
                properties.setProperty(prefix + "id", trip.getId());
                properties.setProperty(prefix + "destination", trip.getDestination());
                properties.setProperty(prefix + "start", trip.getStartDate().toString());
                properties.setProperty(prefix + "end", trip.getEndDate().toString());
                properties.setProperty(prefix + "note", trip.getNote());
                properties.setProperty(prefix + "reminder", Boolean.toString(trip.isReminderEnabled()));
            } catch (RuntimeException error) { throw new IOException("Trip list contains an invalid record."); }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(new LimitedOutputStream(bytes),
                StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            properties.store(writer, "Wayfarer local trips");
        }

        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Trip storage directory cannot be created.");
        Path temporary = File.createTempFile(".trips-", ".tmp", directory).toPath();
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                bytes.writeTo(output);
                output.flush();
                output.getFD().sync();
            }
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Device storage cannot safely replace the trip file.");
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized void deleteAll() throws IOException {
        checkDirectory();
        Files.deleteIfExists(file.toPath());
    }

    private void checkDirectory() throws IOException {
        if (directory.exists() && !directory.isDirectory()) throw new IOException("Trip storage directory is unavailable.");
    }

    private static List<Trip> decode(Properties properties) throws IOException {
        if (!VERSION.equals(properties.getProperty("version"))) throw new IOException("Trip file version is missing or unsupported.");
        String countText = properties.getProperty("count");
        if (countText == null || !countText.matches("[0-9]{1,5}")) throw new IOException("Trip count is missing or invalid.");
        int count = Integer.parseInt(countText);
        if (count > MAX_RECORDS) throw new IOException("Trip count exceeds the storage limit.");
        List<Trip> trips = new ArrayList<>(count);
        Set<String> recordKeys = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Set<String> expected = new HashSet<>();
        expected.add("version");
        expected.add("count");
        for (int i = 0; i < count; i++) {
            String recordName = "record." + i;
            expected.add(recordName);
            String key = required(properties, recordName);
            try {
                if (!UUID.fromString(key).toString().equals(key) || !recordKeys.add(key)) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) { throw new IOException("Trip file contains an invalid record key."); }
            String prefix = "trip." + key + ".";
            for (String field : new String[] {"id", "destination", "start", "end", "note", "reminder"}) expected.add(prefix + field);
            String reminder = required(properties, prefix + "reminder");
            if (!reminder.equals("true") && !reminder.equals("false")) throw new IOException("Trip file contains an invalid reminder setting.");
            try {
                Trip trip = new Trip(required(properties, prefix + "id"), required(properties, prefix + "destination"),
                        required(properties, prefix + "start"), required(properties, prefix + "end"),
                        required(properties, prefix + "note"), Boolean.parseBoolean(reminder));
                if (!ids.add(trip.getId())) throw new IOException("Trip file contains duplicate identifiers.");
                trips.add(trip);
            } catch (RuntimeException error) { throw new IOException("Trip file contains an invalid record."); }
        }
        if (!properties.stringPropertyNames().equals(expected)) throw new IOException("Trip file contains unexpected or incomplete fields.");
        return trips;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) throw new IOException("Trip file is missing a required field.");
        return value;
    }

    private static final class StrictProperties extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) throw new IllegalArgumentException("Duplicate field.");
            return super.put(key, value);
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream output;
        private int count;
        LimitedOutputStream(ByteArrayOutputStream output) { this.output = output; }
        @Override public void write(int value) throws IOException {
            reserve(1);
            output.write(value);
        }
        @Override public void write(byte[] buffer, int offset, int length) throws IOException {
            reserve(length);
            output.write(buffer, offset, length);
        }
        private void reserve(int length) throws IOException {
            if (length > MAX_BYTES - count) throw new IOException("Trip file exceeds the 2 MiB limit.");
            count += length;
        }
    }
}
