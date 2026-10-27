package org.wayfarer.atlas.domain;

public final class AccessSummary {
    private final String citizenship;
    private final int[] counts;

    AccessSummary(String citizenship, int[] counts) {
        this.citizenship = citizenship;
        this.counts = counts.clone();
    }

    public String getCitizenship() { return citizenship; }
    public int getAccessible() { return getVisaFree() + getVisaOnArrival() + getEta(); }
    public int getVisaFree() { return counts[EntryCategory.VISA_FREE.ordinal()]; }
    public int getVisaOnArrival() { return counts[EntryCategory.VISA_ON_ARRIVAL.ordinal()]; }
    public int getEta() { return counts[EntryCategory.ETA.ordinal()]; }
    public int getEVisa() { return counts[EntryCategory.E_VISA.ordinal()]; }
    public int getVisaRequired() { return counts[EntryCategory.VISA_REQUIRED.ordinal()]; }
    public int getRestricted() { return counts[EntryCategory.RESTRICTED.ordinal()]; }
    public int getUnknown() { return counts[EntryCategory.UNKNOWN.ordinal()]; }
    public int getTotal() {
        int total = 0;
        for (int count : counts) total += count;
        return total;
    }
    /** Complete only within the supplied destination universe; never implies global coverage. */
    public boolean isComplete() { return getUnknown() == 0; }
}
