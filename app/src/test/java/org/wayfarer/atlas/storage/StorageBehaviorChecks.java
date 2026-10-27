package org.wayfarer.atlas.storage;

import org.wayfarer.atlas.domain.Trip;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Real temporary-file scenarios shared by the offline harness and JUnit. */
public final class StorageBehaviorChecks {
    private static int passed;

    private StorageBehaviorChecks() { }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("PASS: " + passed + " storage behavior checks");
    }

    public static void runAll() throws Exception {
        passed = 0;
        check("new store is an empty journal", StorageBehaviorChecks::emptyStore);
        check("unicode notes and reminder settings survive restart", StorageBehaviorChecks::roundTrip);
        check("edits and record deletion persist", StorageBehaviorChecks::editAndDelete);
        check("deleteAll clears the journal", StorageBehaviorChecks::deleteAll);
        check("corrupt source is reported and never replaced", StorageBehaviorChecks::corruptSource);
        check("serialized byte limit preserves the previous journal", StorageBehaviorChecks::serializedByteLimit);
        check("invalid record is rejected without disclosing its contents", StorageBehaviorChecks::invalidRecord);
        check("oversized file is rejected without modification", StorageBehaviorChecks::oversizedFile);
        check("failed duplicate save preserves the previous journal", StorageBehaviorChecks::duplicateSave);
        check("oversized replacement preserves the previous journal", StorageBehaviorChecks::oversizedReplacement);
        check("malformed properties and duplicate fields are rejected", StorageBehaviorChecks::malformedProperties);
        check("concurrent replacements never expose partial journals", StorageBehaviorChecks::concurrentReplacement);
    }

    private static void emptyStore() throws Exception {
        inDirectory(directory -> {
            equal(0, new TripStore(directory).load().size());
            truth(!new File(directory, "trips.properties").exists(), "reading a new store must not create data");
        });
    }

    private static void roundTrip() throws Exception {
        inDirectory(directory -> {
            new TripStore(directory).save(Arrays.asList(
                    trip("first", "TH", "Маршрут: Бангкок\n海辺 ☀ = : \\" , true),
                    trip("second", "TR", "", false)));
            List<Trip> loaded = new TripStore(directory).load();
            equal(2, loaded.size());
            equal("first", loaded.get(0).getId());
            equal("TH", loaded.get(0).getDestination());
            equal("2026-11-01", loaded.get(0).getStartDate().toString());
            equal("2026-11-05", loaded.get(0).getEndDate().toString());
            equal("Маршрут: Бангкок\n海辺 ☀ = : \\", loaded.get(0).getNote());
            truth(loaded.get(0).isReminderEnabled(), "enabled reminder must survive storage");
            equal("second", loaded.get(1).getId());
            truth(!loaded.get(1).isReminderEnabled(), "disabled reminder must survive storage");
            equal(1, directory.list().length);
        });
    }

    private static void editAndDelete() throws Exception {
        inDirectory(directory -> {
            TripStore store = new TripStore(directory);
            store.save(Arrays.asList(trip("one", "TH", "initial", false), trip("two", "TR", "remove", false)));
            store.save(Collections.singletonList(trip("one", "JP", "edited", true)));
            List<Trip> edited = new TripStore(directory).load();
            equal(1, edited.size());
            equal("one", edited.get(0).getId());
            equal("JP", edited.get(0).getDestination());
            equal("edited", edited.get(0).getNote());
            store.save(Collections.emptyList());
            equal(0, store.load().size());
        });
    }

    private static void deleteAll() throws Exception {
        inDirectory(directory -> {
            TripStore store = new TripStore(directory);
            store.save(Collections.singletonList(trip("one", "TH", "", true)));
            store.deleteAll();
            equal(0, new TripStore(directory).load().size());
            truth(!new File(directory, "trips.properties").exists(), "deleteAll must remove persisted journal");
            store.deleteAll();
            store.save(Collections.singletonList(trip("next", "JP", "", false)));
            equal("next", store.load().get(0).getId());
        });
    }

    private static void corruptSource() throws Exception {
        inDirectory(directory -> {
            Path path = new File(directory, "trips.properties").toPath();
            byte[] corrupt = "version=99\ncount=0\n".getBytes(StandardCharsets.UTF_8);
            Files.write(path, corrupt);
            TripStore store = new TripStore(directory);
            fails(store::load);
            fails(() -> store.save(Collections.singletonList(trip("new", "TH", "", false))));
            truth(Arrays.equals(corrupt, Files.readAllBytes(path)), "corrupt original must remain recoverable");
        });
    }

    private static void invalidRecord() throws Exception {
        inDirectory(directory -> {
            String secretMarker = "private-note-not-for-errors";
            Properties properties = validProperties();
            String prefix = "trip." + properties.getProperty("record.0") + ".";
            properties.setProperty(prefix + "start", "2026-02-30");
            properties.setProperty(prefix + "note", secretMarker);
            Path path = writeProperties(directory, properties);
            byte[] before = Files.readAllBytes(path);
            IOException error = fails(() -> new TripStore(directory).load());
            truth(!error.getMessage().contains(secretMarker), "error must not contain trip details");
            truth(Arrays.equals(before, Files.readAllBytes(path)), "invalid source must not be rewritten");
        });
    }

    private static void serializedByteLimit() throws Exception {
        inDirectory(directory -> {
            TripStore store = new TripStore(directory);
            store.save(Collections.singletonList(trip("original", "TH", "", false)));
            Path path = new File(directory, "trips.properties").toPath();
            byte[] before = Files.readAllBytes(path);
            char[] letters = new char[1000];
            Arrays.fill(letters, 'Ж');
            String note = new String(letters);
            List<Trip> large = new ArrayList<>();
            for (int i = 0; i < 1200; i++) large.add(trip("large-" + i, "JP", note, false));
            fails(() -> store.save(large));
            truth(Arrays.equals(before, Files.readAllBytes(path)), "oversized encoded data must not replace original");
            equal(1, directory.list().length);
        });
    }

    private static void oversizedFile() throws Exception {
        inDirectory(directory -> {
            Path path = new File(directory, "trips.properties").toPath();
            byte[] oversized = new byte[2 * 1024 * 1024 + 1];
            Arrays.fill(oversized, (byte) 'x');
            Files.write(path, oversized);
            TripStore store = new TripStore(directory);
            fails(store::load);
            fails(() -> store.save(Collections.emptyList()));
            truth(Arrays.equals(oversized, Files.readAllBytes(path)), "oversized original must be preserved");
        });
    }

    private static void duplicateSave() throws Exception {
        inDirectory(directory -> {
            TripStore store = new TripStore(directory);
            store.save(Collections.singletonList(trip("original", "TH", "saved", false)));
            Path path = new File(directory, "trips.properties").toPath();
            byte[] before = Files.readAllBytes(path);
            fails(() -> store.save(Arrays.asList(trip("duplicate", "TH", "", false), trip("duplicate", "JP", "", true))));
            truth(Arrays.equals(before, Files.readAllBytes(path)), "failed validation must preserve original bytes");
            equal("original", store.load().get(0).getId());
            equal(1, directory.list().length);
        });
    }

    private static void oversizedReplacement() throws Exception {
        inDirectory(directory -> {
            TripStore store = new TripStore(directory);
            store.save(Collections.singletonList(trip("original", "TH", "saved", false)));
            byte[] before = Files.readAllBytes(new File(directory, "trips.properties").toPath());
            List<Trip> tooMany = new ArrayList<>();
            for (int i = 0; i < 5001; i++) tooMany.add(trip("trip-" + i, "JP", "", false));
            fails(() -> store.save(tooMany));
            truth(Arrays.equals(before, Files.readAllBytes(new File(directory, "trips.properties").toPath())),
                    "failed capacity check must preserve original bytes");
            equal(1, directory.list().length);
        });
    }

    private static void malformedProperties() throws Exception {
        inDirectory(directory -> {
            Path path = new File(directory, "trips.properties").toPath();
            for (String content : Arrays.asList("version=1\ncount=0\ncount=1\n", "version=1\ncount=-1\n", "version=1\ncount=0\nextra=1\n", "version=1\ncount=5001\n", "version=1\ncount=1\n", "version=1\ncount=0\nnote=\\uZZZZ\n")) {
                Files.write(path, content.getBytes(StandardCharsets.UTF_8));
                fails(() -> new TripStore(directory).load());
            }
            Properties malformedBoolean = validProperties();
            malformedBoolean.setProperty("trip." + malformedBoolean.getProperty("record.0") + ".reminder", "perhaps");
            writeProperties(directory, malformedBoolean);
            fails(() -> new TripStore(directory).load());
        });
    }

    private static void concurrentReplacement() throws Exception {
        inDirectory(directory -> {
            TripStore writer = new TripStore(directory);
            TripStore reader = new TripStore(directory);
            writer.save(Collections.singletonList(trip("old", "TH", "", false)));
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread read = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        List<Trip> loaded = reader.load();
                        truth(loaded.size() == 1, "reader must always see one complete record");
                        truth(Arrays.asList("old", "new").contains(loaded.get(0).getId()), "reader must see old or new journal");
                    }
                } catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            read.start();
            start.countDown();
            for (int i = 0; i < 30; i++) writer.save(Collections.singletonList(trip(i % 2 == 0 ? "new" : "old", "TH", "", false)));
            read.join();
            if (failure.get() != null) throw new AssertionError("reader observed a partial replacement", failure.get());
        });
    }

    private static Properties validProperties() {
        Properties value = new Properties();
        String record = UUID.randomUUID().toString();
        value.setProperty("version", "1");
        value.setProperty("count", "1");
        value.setProperty("record.0", record);
        String prefix = "trip." + record + ".";
        value.setProperty(prefix + "id", "original");
        value.setProperty(prefix + "destination", "TH");
        value.setProperty(prefix + "start", "2026-11-01");
        value.setProperty(prefix + "end", "2026-11-05");
        value.setProperty(prefix + "note", "");
        value.setProperty(prefix + "reminder", "false");
        return value;
    }

    private static Path writeProperties(File directory, Properties value) throws IOException {
        Path path = new File(directory, "trips.properties").toPath();
        try (java.io.Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { value.store(writer, "fixture"); }
        return path;
    }

    private static Trip trip(String id, String destination, String note, boolean reminder) {
        return new Trip(id, destination, "2026-11-01", "2026-11-05", note, reminder);
    }

    private static IOException fails(CheckedAction action) throws Exception {
        try { action.run(); } catch (IOException expected) {
            truth(expected.getMessage() != null && !expected.getMessage().isEmpty(), "storage error must be readable");
            return expected;
        }
        throw new AssertionError("expected IOException");
    }

    private static void inDirectory(DirectoryAction action) throws Exception {
        File directory = Files.createTempDirectory("wayfarer-storage-check-").toFile();
        try { action.run(directory); }
        finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
                });
            }
        }
    }

    private static void check(String name, CheckedAction action) throws Exception {
        try { action.run(); passed++; }
        catch (Throwable failure) { throw new AssertionError(name + ": " + failure.getMessage(), failure); }
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected value does not match stored value");
    }
    private static void truth(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface CheckedAction { void run() throws Exception; }
    private interface DirectoryAction { void run(File directory) throws Exception; }
}
