package org.wayfarer.atlas.data;

import java.util.Set;
import org.wayfarer.atlas.domain.EntryCategory;

public final class CommunityMatrixBehaviorChecks {
    private static int passed;
    public static void main(String[] args) throws Exception {
        runAll();
        if (args.length >= 2) {
            var countries = DatasetParser.parseCountries(read(java.nio.file.Path.of(args[1])));
            Set<String> codes = new java.util.HashSet<>();
            for (var country : countries) codes.add(country.getCode());
            var snapshot = CommunityMatrixParser.parse(read(java.nio.file.Path.of(args[0])), codes);
            equal(38612, snapshot.getRules().size());
            equal(38L, snapshot.getRules().stream().filter(r -> r.getCheckedAt() != null).count());
            System.out.println("Real community source parses: " + snapshot.getRules().size() + " rules; 38 verification dates.");
            if (args.length == 3) {
                var canonical = CommunityMatrixParser.normalize(read(java.nio.file.Path.of(args[0])), codes);
                var expected = read(java.nio.file.Path.of(args[2]));
                equal(com.google.gson.JsonParser.parseString(expected), com.google.gson.JsonParser.parseString(canonical));
                System.out.println("Python/native parity: all categories, dates, source URLs, durations and notes match.");
            }
        }
        System.out.println("PASS: " + passed + " community matrix checks");
    }
    public static void runAll() {
        passed = 0;
        check("sourced arrival visa keeps date and duration", () -> {
            var snapshot = parse("{\"requirement\":\"visa_on_arrival\",\"max_stay_days\":15,\"verified\":\"2026-07-01\",\"source\":\"https://example.gov/rule\"}");
            var rule = snapshot.getRules().get(0);
            equal(EntryCategory.VISA_ON_ARRIVAL, rule.getCategory()); equal(Integer.valueOf(15), rule.getMaxStayDays());
            equal("2026-07-01", rule.getCheckedAt()); equal("https://example.gov/rule", rule.getSourceUrl()); equal("2026-08-10", snapshot.getUpdatedAt());
        });
        check("unverified base is explicit", () -> {
            var rule = parse("{\"requirement\":\"evisa\",\"max_stay_days\":null}").getRules().get(0);
            equal(EntryCategory.E_VISA, rule.getCategory()); equal(null, rule.getCheckedAt());
            equal(CommunityMatrixParser.SOURCE_URL, rule.getSourceUrl());
            if (!rule.getNotes().contains("Not individually verified")) throw new AssertionError("Missing provenance limit");
        });
        check("unknown is not treated as free", () -> equal(EntryCategory.UNKNOWN, parse("{\"requirement\":\"upstream_changed\"}").getRules().get(0).getCategory()));
        check("http source remains text", () -> {
            var rule = parse("{\"requirement\":\"visa_free\",\"max_stay_days\":730,\"source\":\"http://example.gov/rule\"}").getRules().get(0);
            equal(Integer.valueOf(730), rule.getMaxStayDays()); equal(CommunityMatrixParser.SOURCE_URL, rule.getSourceUrl());
            if (!rule.getNotes().contains("http://example.gov/rule")) throw new AssertionError("Source lost");
        });
        check("invalid date rejects entire update", () -> reject(() -> parse("{\"requirement\":\"visa_free\",\"verified\":\"2026-02-31\"}")));
        check("fractional duration rejects entire update", () -> reject(() -> parse("{\"requirement\":\"visa_free\",\"max_stay_days\":1.5}")));
        check("unknown country rejects update", () -> reject(() -> CommunityMatrixParser.parse(document("{\"requirement\":\"visa_free\"}"), Set.of("RU"))));
        check("wrong license rejects update", () -> reject(() -> CommunityMatrixParser.parse(document("{\"requirement\":\"visa_free\"}").replace("CC-BY-4.0", "restricted"), Set.of("RU", "TH"))));
        check("string duration rejects update", () -> reject(() -> parse("{\"requirement\":\"visa_free\",\"max_stay_days\":\"30\"}")));
    }
    private static RuleSnapshot parse(String row) { return CommunityMatrixParser.parse(document(row), Set.of("RU", "TH")); }
    private static String document(String row) { return "{\"name\":\"visa-requirements\",\"license\":\"CC-BY-4.0\",\"updated\":\"2026-08-10\",\"rules\":{\"RU\":{\"TH\":" + row + "}}}"; }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("expected rejection"); }
    private static void equal(Object expected, Object actual) { if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + " but was " + actual); }
    private static void check(String name, Runnable action) { try { action.run(); passed++; } catch (Throwable t) { throw new AssertionError(name + ": " + t.getMessage(), t); } }
    private static String read(java.nio.file.Path path) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

}
