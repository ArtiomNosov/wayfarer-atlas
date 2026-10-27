package org.wayfarer.atlas.domain;

public final class EntryRule {
    private final String citizenship;
    private final String destination;
    private final EntryCategory category;
    private final Integer maxStayDays;
    private final String notes;
    private final String sourceUrl;
    private final String checkedAt;
    private final String notesRu;
    private final java.util.List<RuleCitation> citations;

    public EntryRule(String citizenship, String destination, EntryCategory category) {
        this(citizenship, destination, category, null, "", null, null);
    }

    public EntryRule(String citizenship, String destination, EntryCategory category, Integer maxStayDays, String notes, String sourceUrl, String checkedAt) {
        this(citizenship, destination, category, maxStayDays, notes, sourceUrl, checkedAt, "",
                sourceUrl == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(
                        new RuleCitation("", "", sourceUrl, checkedAt, false)));
    }

    public EntryRule(String citizenship, String destination, EntryCategory category, Integer maxStayDays, String notes, String sourceUrl, String checkedAt,
                     String notesRu, java.util.List<RuleCitation> citations) {
        this.citizenship = citizenship;
        this.destination = destination;
        this.category = category;
        this.maxStayDays = maxStayDays;
        this.notes = notes;
        this.sourceUrl = sourceUrl;
        this.checkedAt = checkedAt;
        this.notesRu = notesRu == null ? "" : notesRu;
        this.citations = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(citations));
    }

    public String getCitizenship() { return citizenship; }
    public String getDestination() { return destination; }
    public EntryCategory getCategory() { return category; }
    public Integer getMaxStayDays() { return maxStayDays; }
    public String getNotes() { return notes; }
    public String getSourceUrl() { return sourceUrl; }
    public String getCheckedAt() { return checkedAt; }
    public String getNotesRu() { return notesRu; }
    public java.util.List<RuleCitation> getCitations() { return citations; }
}
