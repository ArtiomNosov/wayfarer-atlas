package org.wayfarer.atlas.data;
import java.util.Arrays;
import java.util.Collections;
import org.wayfarer.atlas.domain.EntryCategory;
import org.wayfarer.atlas.domain.EntryRule;
public final class UpdatePolicyBehaviorChecks {
    public static void main(String[] args) { runAll(); System.out.println("PASS: 6 update policy checks"); }
    public static void runAll() {
        RuleSnapshot known = snapshot("2026-08-10", EntryCategory.VISA_FREE);
        ReferenceUpdatePolicy.validate("2026-08-10", known, 1, 1, 1);
        reject(() -> ReferenceUpdatePolicy.validate("2026-08-11", known, 1, 1, 1));
        reject(() -> ReferenceUpdatePolicy.validate("2026-08-10", snapshot(null, EntryCategory.VISA_FREE), 1, 1, 1));
        reject(() -> ReferenceUpdatePolicy.validate(null, snapshot(null, EntryCategory.UNKNOWN), 1, 1, 1));
        reject(() -> ReferenceUpdatePolicy.validate(null, known, 2, 1, 1));
        reject(() -> ReferenceUpdatePolicy.validate(null, known, 1, 2, 1));
    }
    private static RuleSnapshot snapshot(String date, EntryCategory category) { return new RuleSnapshot(Arrays.asList(new EntryRule("RU", "TH", category)), "https://example.org/reference", date); }
    private static void reject(Runnable action) { try { action.run(); } catch(IllegalArgumentException expected) { return; } throw new AssertionError("Expected invalid update to be rejected before replacing the cache"); }
}
