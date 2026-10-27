package org.wayfarer.atlas.domain;

public final class TripSummary {
    private final int tripCount;
    private final int countriesVisited;
    private final long totalTripDays;
    private final long distinctTravelDays;

    TripSummary(int tripCount, int countriesVisited, long totalTripDays, long distinctTravelDays) {
        this.tripCount = tripCount;
        this.countriesVisited = countriesVisited;
        this.totalTripDays = totalTripDays;
        this.distinctTravelDays = distinctTravelDays;
    }

    public int getTripCount() { return tripCount; }
    public int getCountriesVisited() { return countriesVisited; }
    /** Per-trip inclusive days; overlapping trips each contribute their own duration. */
    public long getTotalTripDays() { return totalTripDays; }
    /** Union of calendar dates covered by any trip, counting overlaps once. */
    public long getDistinctTravelDays() { return distinctTravelDays; }
}
