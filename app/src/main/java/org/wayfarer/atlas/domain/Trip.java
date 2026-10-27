package org.wayfarer.atlas.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public final class Trip {
    private final String id;
    private final String destination;
    private final String startDate;
    private final String endDate;
    private final String note;
    private final boolean reminderEnabled;
    private final LocalDate start;
    private final LocalDate end;

    public Trip(String id, String destination, String startDate, String endDate) {
        this(id, destination, startDate, endDate, "", false);
    }

    public Trip(String id, String destination, String startDate, String endDate, String note, boolean reminderEnabled) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Trip identifier is required");
        if (destination == null || destination.trim().isEmpty()) throw new IllegalArgumentException("Trip destination is required");
        if (note == null) throw new IllegalArgumentException("Trip note must not be null");
        this.start = parseDate(startDate);
        this.end = parseDate(endDate);
        if (end.isBefore(start)) throw new IllegalArgumentException("Trip end must not precede start");
        this.id = id;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.note = note;
        this.reminderEnabled = reminderEnabled;
    }

    public String getId() { return id; }
    public String getDestination() { return destination; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getNote() { return note; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    /** Calendar days including arrival and departure, so a same-day trip is one day. */
    public long getInclusiveDays() { return ChronoUnit.DAYS.between(start, end) + 1; }

    LocalDate start() { return start; }
    LocalDate end() { return end; }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("Trip dates must use YYYY-MM-DD");
        }
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException invalid) { throw new IllegalArgumentException("Trip date is not a valid calendar day", invalid); }
    }
}
