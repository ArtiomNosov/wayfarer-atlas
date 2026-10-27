package org.wayfarer.atlas.data;
public final class SnapshotSelectionChecks {
    public static void main(String[] args) { runAll(); System.out.println("PASS: 7 snapshot selection checks"); }
    public static void runAll() {
        equal(false, SnapshotSelection.preferCache(true, "2026-08-10", "2026-10-25"));
        equal(true, SnapshotSelection.preferCache(true, "2026-10-25", "2026-08-10"));
        equal(false, SnapshotSelection.preferCache(true, null, "2026-08-10"));
        equal(false, SnapshotSelection.preferCache(false, null, "2026-08-10"));
        equal(true, SnapshotSelection.preferCache(true, "2026-08-10", "2026-08-10"));
        equal(true, SnapshotSelection.preferCache(true, "2026-08-10", null));
        equal("2026-08-10", SnapshotSelection.publicationDate("{\"updatedAt\":\"2026-08-10\",\"rules\":[]}"));
    }
    private static void equal(Object expected, Object actual) { if(!expected.equals(actual)) throw new AssertionError("expected " + expected + " but was " + actual); }
}
