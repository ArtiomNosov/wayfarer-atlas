package org.wayfarer.atlas.data;
import java.util.List;
import org.wayfarer.atlas.domain.EntryRule;
public final class RuleSnapshot {
    private final List<EntryRule> rules;
    private final String sourceUrl;
    private final String updatedAt;
    private final String sourceAsAt;
    public RuleSnapshot(List<EntryRule> rules, String sourceUrl, String updatedAt) {
        this(rules, sourceUrl, updatedAt, null);
    }
    public RuleSnapshot(List<EntryRule> rules, String sourceUrl, String updatedAt, String sourceAsAt) {
        this.rules = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(rules));
        this.sourceUrl = sourceUrl;
        this.updatedAt = updatedAt;
        this.sourceAsAt = sourceAsAt;
    }
    public List<EntryRule> getRules() { return rules; }
    public String getSourceUrl() { return sourceUrl; }
    public String getUpdatedAt() { return updatedAt; }
    public String getSourceAsAt() { return sourceAsAt; }
}
