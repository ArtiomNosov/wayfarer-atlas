package org.wayfarer.atlas.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dependency-free behavior suite; also called by JUnit in the Android build. */
public final class DomainBehaviorChecks {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        runAll();
        System.out.println("PASS: " + passed + " domain behavior checks");
    }

    public static void runAll() {
        passed = 0;
        failed = 0;
        check("accessible metric keeps e-visa separate", DomainBehaviorChecks::accessCategories);
        check("missing destinations remain unknown", DomainBehaviorChecks::missingRules);
        check("complete coverage requires known rules", DomainBehaviorChecks::completeCoverage);
        check("conflicting rules remain unknown", DomainBehaviorChecks::conflictingRules);
        check("comparison excludes both citizenship origins", DomainBehaviorChecks::comparison);
        check("unknown comparison side is not counted as inaccessible", DomainBehaviorChecks::unknownComparison);
        check("same-day trip occupies one calendar day", DomainBehaviorChecks::sameDayTrip);
        check("trip length crosses leap day", DomainBehaviorChecks::leapDayTrip);
        check("invalid calendar date is rejected", () -> rejects(() -> trip("a", "TH", "2025-02-29", "2025-03-01")));
        check("non-ISO date is rejected", () -> rejects(() -> trip("a", "TH", "25.10.2026", "2026-10-26")));
        check("short ISO fields are rejected", () -> rejects(() -> trip("a", "TH", "2026-1-2", "2026-01-03")));
        check("reversed trip is rejected", () -> rejects(() -> trip("a", "TH", "2026-10-27", "2026-10-25")));
        check("empty trip identifier is rejected", () -> rejects(() -> trip(" ", "TH", "2026-10-25", "2026-10-26")));
        check("overlaps count once in distinct travel days", DomainBehaviorChecks::overlapStatistics);
        check("empty journal has zero statistics", DomainBehaviorChecks::emptyStatistics);
        check("reminders include today and future enabled trips only", DomainBehaviorChecks::futureReminders);
        if (failed != 0) throw new AssertionError(failed + " domain behavior checks failed");
    }

    private static void accessCategories() {
        AccessSummary summary = EntryRules.summarize("RU", Arrays.asList("RU", "TH", "TR", "AE", "IN", "JP", "KP"), Arrays.asList(
                rule("RU", "RU", EntryCategory.VISA_FREE),
                rule("RU", "TH", EntryCategory.VISA_FREE),
                rule("RU", "TR", EntryCategory.VISA_ON_ARRIVAL),
                rule("RU", "AE", EntryCategory.ETA),
                rule("RU", "IN", EntryCategory.E_VISA),
                rule("RU", "JP", EntryCategory.VISA_REQUIRED),
                rule("RU", "KP", EntryCategory.RESTRICTED)));
        equal(3, summary.getAccessible());
        equal(1, summary.getVisaFree());
        equal(1, summary.getVisaOnArrival());
        equal(1, summary.getEta());
        equal(1, summary.getEVisa());
        equal(1, summary.getVisaRequired());
        equal(1, summary.getRestricted());
        equal(6, summary.getTotal());
    }

    private static void missingRules() {
        AccessSummary summary = EntryRules.summarize("RU", Arrays.asList("RU", "TH", "TH", "CA", "XX"), Arrays.asList(
                rule("RU", "TH", EntryCategory.VISA_FREE),
                rule("US", "CA", EntryCategory.VISA_FREE),
                rule("RU", "XX", EntryCategory.UNKNOWN)));
        equal(3, summary.getTotal());
        equal(1, summary.getAccessible());
        equal(2, summary.getUnknown());
        truth(!summary.isComplete(), "incomplete matrix cannot be marked complete");
    }

    private static void completeCoverage() {
        AccessSummary summary = EntryRules.summarize("RU", Arrays.asList("RU", "TH", "JP"), Arrays.asList(
                rule("RU", "TH", EntryCategory.VISA_FREE),
                rule("RU", "JP", EntryCategory.VISA_REQUIRED)));
        truth(summary.isComplete(), "known complete coverage must be marked complete");
        equal(0, summary.getUnknown());
    }

    private static void conflictingRules() {
        AccessSummary summary = EntryRules.summarize("RU", Arrays.asList("TH"), Arrays.asList(
                rule("RU", "TH", EntryCategory.VISA_FREE),
                rule("RU", "TH", EntryCategory.VISA_REQUIRED)));
        equal(0, summary.getAccessible());
        equal(1, summary.getUnknown());
    }

    private static void comparison() {
        CountryComparison comparison = EntryRules.compare("RU", "US", Arrays.asList("RU", "US", "TH", "TR", "AE", "JP"), Arrays.asList(
                rule("RU", "TH", EntryCategory.VISA_FREE), rule("US", "TH", EntryCategory.VISA_REQUIRED),
                rule("RU", "TR", EntryCategory.ETA), rule("US", "TR", EntryCategory.VISA_ON_ARRIVAL),
                rule("RU", "AE", EntryCategory.E_VISA), rule("US", "AE", EntryCategory.VISA_FREE),
                rule("RU", "JP", EntryCategory.VISA_REQUIRED), rule("US", "JP", EntryCategory.VISA_REQUIRED),
                rule("RU", "US", EntryCategory.VISA_FREE), rule("US", "RU", EntryCategory.VISA_FREE)));
        equal(set("TH"), comparison.getFirstOnly());
        equal(set("TR"), comparison.getBoth());
        equal(set("AE"), comparison.getSecondOnly());
        equal(Collections.emptySet(), comparison.getUnknown());
    }

    private static void unknownComparison() {
        CountryComparison comparison = EntryRules.compare("RU", "US", Arrays.asList("TH", "TR"), Arrays.asList(
                rule("RU", "TH", EntryCategory.VISA_FREE),
                rule("US", "TR", EntryCategory.VISA_FREE), rule("RU", "TR", EntryCategory.UNKNOWN)));
        equal(set("TH", "TR"), comparison.getUnknown());
        equal(Collections.emptySet(), comparison.getFirstOnly());
        equal(Collections.emptySet(), comparison.getSecondOnly());
    }

    private static void sameDayTrip() { equal(1L, trip("a", "TH", "2026-10-25", "2026-10-25").getInclusiveDays()); }
    private static void leapDayTrip() { equal(3L, trip("a", "TH", "2024-02-28", "2024-03-01").getInclusiveDays()); }

    private static void overlapStatistics() {
        TripSummary summary = TripStatistics.summarize(Arrays.asList(
                trip("a", "TH", "2026-01-01", "2026-01-03"),
                trip("b", "TR", "2026-01-03", "2026-01-05"),
                trip("c", "TH", "2026-01-04", "2026-01-04"),
                trip("d", "TH", "2026-01-07", "2026-01-08")));
        equal(4, summary.getTripCount());
        equal(2, summary.getCountriesVisited());
        equal(9L, summary.getTotalTripDays());
        equal(7L, summary.getDistinctTravelDays());
    }

    private static void emptyStatistics() {
        TripSummary summary = TripStatistics.summarize(Collections.emptyList());
        equal(0, summary.getTripCount());
        equal(0, summary.getCountriesVisited());
        equal(0L, summary.getTotalTripDays());
        equal(0L, summary.getDistinctTravelDays());
    }

    private static void futureReminders() {
        Trip past = new Trip("past", "TH", "2026-10-24", "2026-10-26", "", true);
        Trip today = new Trip("today", "TH", "2026-10-25", "2026-10-26", "", true);
        Trip future = new Trip("future", "TR", "2026-11-01", "2026-11-03", "", true);
        Trip disabled = new Trip("disabled", "TR", "2026-10-26", "2026-10-28", "", false);
        List<Trip> result = TripStatistics.reminders(Arrays.asList(future, past, disabled, today), LocalDate.of(2026, 10, 25));
        equal(Arrays.asList(today, future), result);
    }

    private static EntryRule rule(String citizenship, String destination, EntryCategory category) {
        return new EntryRule(citizenship, destination, category);
    }
    private static Trip trip(String id, String destination, String start, String end) { return new Trip(id, destination, start, end); }
    private static Set<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected invalid trip to be rejected");
    }
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }
    private static void truth(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void check(String name, Runnable action) {
        try { action.run(); passed++; }
        catch (Throwable failure) {
            failed++;
            System.err.println("FAIL: " + name + ": " + failure.getMessage());
        }
    }
}
