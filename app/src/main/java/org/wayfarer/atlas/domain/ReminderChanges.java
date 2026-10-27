package org.wayfarer.atlas.domain;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
public final class ReminderChanges {
    private ReminderChanges() {}
    public static List<Trip> toSchedule(List<Trip> before, List<Trip> after) {
        Map<String, Trip> old = index(before);
        List<Trip> result = new ArrayList<>();
        for (Trip trip : after) {
            Trip previous = old.get(trip.getId());
            if (trip.isReminderEnabled() && (previous == null || !previous.isReminderEnabled()
                    || !previous.getStartDate().equals(trip.getStartDate()))) result.add(trip);
        }
        return result;
    }
    public static List<String> toCancel(List<Trip> before, List<Trip> after) {
        Map<String, Trip> current = index(after);
        List<String> result = new ArrayList<>();
        for (Trip previous : before) {
            Trip trip = current.get(previous.getId());
            if (trip == null || (previous.isReminderEnabled() && !trip.isReminderEnabled())) result.add(previous.getId());
        }
        return result;
    }
    private static Map<String, Trip> index(List<Trip> trips) {
        Map<String, Trip> result = new HashMap<>();
        for (Trip trip : trips) result.put(trip.getId(), trip);
        return result;
    }
}
