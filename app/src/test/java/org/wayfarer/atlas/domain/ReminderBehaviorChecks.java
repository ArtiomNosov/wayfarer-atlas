package org.wayfarer.atlas.domain;
import java.util.Arrays;
import java.util.Collections;
public final class ReminderBehaviorChecks {
    public static void main(String[] args) { runAll(); System.out.println("PASS: 5 reminder change checks"); }
    public static void runAll() {
        Trip old = trip("a", "2026-10-25", "before", true);
        Trip changedNote = trip("a", "2026-10-25", "after", true);
        equal(0, ReminderChanges.toSchedule(Arrays.asList(old), Arrays.asList(changedNote)).size());
        equal(1, ReminderChanges.toSchedule(Collections.emptyList(), Arrays.asList(old)).size());
        equal(1, ReminderChanges.toSchedule(Arrays.asList(old), Arrays.asList(trip("a", "2026-10-26", "", true))).size());
        equal(Arrays.asList("a"), ReminderChanges.toCancel(Arrays.asList(old), Arrays.asList(trip("a", "2026-10-25", "", false))));
        equal(Arrays.asList("a"), ReminderChanges.toCancel(Arrays.asList(old), Collections.emptyList()));
    }
    private static Trip trip(String id, String date, String note, boolean enabled) { return new Trip(id, "TH", date, date, note, enabled); }
    private static void equal(Object a, Object b) { if (!a.equals(b)) throw new AssertionError("expected " + a + " but was " + b); }
}
