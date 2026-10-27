package org.wayfarer.atlas.domain;

public enum EntryCategory {
    VISA_FREE, VISA_ON_ARRIVAL, ETA, E_VISA, VISA_REQUIRED, RESTRICTED, UNKNOWN;

    /** Comparative access metric, not a synonym for visa-free travel. */
    public boolean isAccessible() {
        return this == VISA_FREE || this == VISA_ON_ARRIVAL || this == ETA;
    }
}
