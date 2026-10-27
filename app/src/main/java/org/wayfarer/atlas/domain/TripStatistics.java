package org.wayfarer.atlas.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TripStatistics {
    private TripStatistics() {}

    public static TripSummary summarize(List<Trip> trips) {
        List<Trip> sorted = new ArrayList<>(trips);
        sorted.sort(Comparator.comparing(Trip::start));
        Set<String> countries = new HashSet<>();
        long totalDays = 0;
        long distinctDays = 0;
        LocalDate unionStart = null;
        LocalDate unionEnd = null;
        for (Trip trip : sorted) {
            countries.add(trip.getDestination());
            totalDays += trip.getInclusiveDays();
            if (unionStart == null) {
                unionStart = trip.start();
                unionEnd = trip.end();
            } else if (!trip.start().isAfter(unionEnd)) {
                if (trip.end().isAfter(unionEnd)) unionEnd = trip.end();
            } else {
                distinctDays += ChronoUnit.DAYS.between(unionStart, unionEnd) + 1;
                unionStart = trip.start();
                unionEnd = trip.end();
            }
        }
        if (unionStart != null) distinctDays += ChronoUnit.DAYS.between(unionStart, unionEnd) + 1;
        return new TripSummary(trips.size(), countries.size(), totalDays, distinctDays);
    }

    /** Pending trip starts, based on the caller's local date, in chronological order. */
    public static List<Trip> reminders(List<Trip> trips, LocalDate today) {
        List<Trip> result = new ArrayList<>();
        for (Trip trip : trips) {
            if (trip.isReminderEnabled() && !trip.start().isBefore(today)) result.add(trip);
        }
        result.sort(Comparator.comparing(Trip::start).thenComparing(Trip::getId));
        return Collections.unmodifiableList(result);
    }
}
