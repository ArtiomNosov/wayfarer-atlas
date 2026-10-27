package org.wayfarer.atlas.data;
import java.util.HashSet;
import java.util.Set;
import org.wayfarer.atlas.domain.EntryCategory;
import org.wayfarer.atlas.domain.EntryRule;
public final class ReferenceUpdatePolicy {
    private ReferenceUpdatePolicy() {}
    public static void validateReplacement(RuleSnapshot previous, RuleSnapshot candidate, int minimumRules, int minimumCitizenships, int minimumKnownRules) {
        validate(previous == null ? null : previous.getUpdatedAt(), candidate, minimumRules, minimumCitizenships, minimumKnownRules);
        if (previous != null && previous.getSourceAsAt() != null
                && (candidate.getSourceAsAt() == null || candidate.getSourceAsAt().compareTo(previous.getSourceAsAt()) < 0)) {
            throw new IllegalArgumentException("Official source state date is older or missing");
        }
    }
    public static void validate(String previousDate, RuleSnapshot candidate, int minimumRules, int minimumCitizenships, int minimumKnownRules) {
        Set<String> origins = new HashSet<>();
        int known = 0;
        for (EntryRule rule : candidate.getRules()) {
            origins.add(rule.getCitizenship());
            if (rule.getCategory() != EntryCategory.UNKNOWN) known++;
        }
        if (candidate.getRules().size() < Math.max(1, minimumRules) || origins.size() < Math.max(1, minimumCitizenships)
                || known < Math.max(1, minimumKnownRules)
                || (previousDate != null && (candidate.getUpdatedAt() == null || candidate.getUpdatedAt().compareTo(previousDate) < 0))) {
            throw new IllegalArgumentException("Reference update has insufficient coverage or is older than the current dataset");
        }
    }
}
