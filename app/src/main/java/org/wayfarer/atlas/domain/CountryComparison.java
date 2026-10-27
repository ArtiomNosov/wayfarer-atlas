package org.wayfarer.atlas.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CountryComparison {
    private final Set<String> firstOnly;
    private final Set<String> both;
    private final Set<String> secondOnly;
    private final Set<String> unknown;

    CountryComparison(Set<String> firstOnly, Set<String> both, Set<String> secondOnly, Set<String> unknown) {
        this.firstOnly = immutable(firstOnly);
        this.both = immutable(both);
        this.secondOnly = immutable(secondOnly);
        this.unknown = immutable(unknown);
    }

    public Set<String> getFirstOnly() { return firstOnly; }
    public Set<String> getBoth() { return both; }
    public Set<String> getSecondOnly() { return secondOnly; }
    public Set<String> getUnknown() { return unknown; }

    private static Set<String> immutable(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
