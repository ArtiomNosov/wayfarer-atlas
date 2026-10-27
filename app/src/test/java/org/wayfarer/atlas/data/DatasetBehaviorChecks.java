package org.wayfarer.atlas.data;

import java.util.Arrays;
import java.util.HashSet;
import org.wayfarer.atlas.domain.EntryCategory;

public final class DatasetBehaviorChecks {
    private static int passed;
    public static void main(String[] args) {
        runAll();
        System.out.println("PASS: " + passed + " dataset behavior checks");
        if (args.length == 1) {
            try {
                java.nio.file.Path directory = java.nio.file.Path.of(args[0]);
                var countries = DatasetParser.parseCountries(read(directory.resolve("countries.json")));
                java.util.Set<String> codes = new java.util.HashSet<>();
                for (var country : countries) codes.add(country.getCode());
                var snapshot = DatasetParser.parseRules(read(directory.resolve("rules.json")), codes);
                if (countries.size() < 240) throw new AssertionError("Packaged country list is incomplete");
                System.out.println("Packaged assets parse: " + countries.size() + " references; " + snapshot.getRules().size() + " real rules.");
            } catch (java.io.IOException failure) { throw new AssertionError("Packaged assets are unreadable", failure); }
        }
    }
    public static void runAll() {
        passed = 0;
        check("localized names", () -> {
            var countries = DatasetParser.parseCountries("{\"countries\":[{\"code\":\"RU\",\"nameEn\":\"Russia\",\"nameRu\":\"Россия\"}]}");
            equal(1, countries.size()); equal("Россия", countries.get(0).getNameRu());
        });
        check("duplicate country rejected", () -> reject(() -> DatasetParser.parseCountries("{\"countries\":[{\"code\":\"RU\",\"nameEn\":\"Russia\",\"nameRu\":\"Россия\"},{\"code\":\"RU\",\"nameEn\":\"Russia\",\"nameRu\":\"Россия\"}]}")));
        check("missing localized country name rejected", () -> reject(() -> DatasetParser.parseCountries("{\"countries\":[{\"code\":\"RU\",\"nameEn\":\"Russia\"}]}")));
        check("unknown is preserved", () -> {
            RuleSnapshot result = rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"UNKNOWN\",\"checkedAt\":null}");
            equal(EntryCategory.UNKNOWN, result.getRules().get(0).getCategory());
            equal(null, result.getRules().get(0).getCheckedAt());
            equal(null, result.getUpdatedAt());
        });
        check("evisa distinct from visa free", () -> equal(EntryCategory.E_VISA, rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"E_VISA\"}").getRules().get(0).getCategory()));
        check("unrecognized category rejected", () -> reject(() -> rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"FREE_MAYBE\"}")));
        check("invalid source URL rejected", () -> reject(() -> DatasetParser.parseRules("{\"version\":1,\"source\":\"http://example.org\",\"rules\":[]}", codes())));
        check("explicit absent snapshot is supported", () -> equal(0, DatasetParser.parseRules("{\"version\":1,\"source\":null,\"rules\":[]}", codes()).getRules().size()));
        check("invalid country pair rejected", () -> reject(() -> rules("{\"citizenship\":\"XX\",\"destination\":\"TH\",\"category\":\"VISA_FREE\"}")));
        check("duplicate matrix cell rejected", () -> reject(() -> rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"VISA_FREE\"},{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"VISA_REQUIRED\"}")));
        check("invalid verified date rejected", () -> reject(() -> rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"VISA_FREE\",\"checkedAt\":\"2026-02-31\"}")));
        check("fractional stay rejected", () -> reject(() -> rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"VISA_FREE\",\"maxStayDays\":1.5}")));
        check("malformed JSON rejected", () -> reject(() -> DatasetParser.parseCountries("broken")));
        check("known stay parsed", () -> equal(Integer.valueOf(30), rules("{\"citizenship\":\"RU\",\"destination\":\"TH\",\"category\":\"VISA_FREE\",\"maxStayDays\":30}").getRules().get(0).getMaxStayDays()));
    }
    private static java.util.Set<String> codes() { return new HashSet<>(Arrays.asList("RU", "TH")); }
    private static RuleSnapshot rules(String json) { return DatasetParser.parseRules("{\"version\":1,\"source\":\"https://example.org/data\",\"updatedAt\":null,\"rules\":[" + json + "]}", codes()); }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("expected rejection"); }
    private static void equal(Object expected, Object actual) { if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + " but was " + actual); }
    private static void check(String name, Runnable action) { try { action.run(); passed++; } catch (Throwable t) { throw new AssertionError(name + ": " + t.getMessage(), t); } }
    private static String read(java.nio.file.Path path) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

}
