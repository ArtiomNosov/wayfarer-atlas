package org.wayfarer.atlas.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EntryRules {
    private EntryRules() {}

    public static AccessSummary summarize(String citizenship, Collection<String> destinations, List<EntryRule> rules) {
        Map<String, EntryCategory> categories = index(citizenship, rules);
        int[] counts = new int[EntryCategory.values().length];
        for (String destination : new LinkedHashSet<>(destinations)) {
            if (!citizenship.equals(destination)) {
                EntryCategory category = categories.getOrDefault(destination, EntryCategory.UNKNOWN);
                counts[category.ordinal()]++;
            }
        }
        return new AccessSummary(citizenship, counts);
    }

    public static CountryComparison compare(String first, String second, Collection<String> destinations, List<EntryRule> rules) {
        Map<String, EntryCategory> firstCategories = index(first, rules);
        Map<String, EntryCategory> secondCategories = index(second, rules);
        Set<String> firstOnly = new LinkedHashSet<>();
        Set<String> both = new LinkedHashSet<>();
        Set<String> secondOnly = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (String destination : new LinkedHashSet<>(destinations)) {
            if (destination.equals(first) || destination.equals(second)) continue;
            EntryCategory firstCategory = firstCategories.getOrDefault(destination, EntryCategory.UNKNOWN);
            EntryCategory secondCategory = secondCategories.getOrDefault(destination, EntryCategory.UNKNOWN);
            if (firstCategory == EntryCategory.UNKNOWN || secondCategory == EntryCategory.UNKNOWN) {
                unknown.add(destination);
            } else if (firstCategory.isAccessible() && secondCategory.isAccessible()) {
                both.add(destination);
            } else if (firstCategory.isAccessible()) {
                firstOnly.add(destination);
            } else if (secondCategory.isAccessible()) {
                secondOnly.add(destination);
            }
        }
        return new CountryComparison(firstOnly, both, secondOnly, unknown);
    }

    private static Map<String, EntryCategory> index(String citizenship, List<EntryRule> rules) {
        Map<String, EntryCategory> categories = new HashMap<>();
        for (EntryRule rule : rules) {
            if (!citizenship.equals(rule.getCitizenship())) continue;
            EntryCategory category = rule.getCategory() == null ? EntryCategory.UNKNOWN : rule.getCategory();
            EntryCategory previous = categories.putIfAbsent(rule.getDestination(), category);
            if (previous != null && previous != category) categories.put(rule.getDestination(), EntryCategory.UNKNOWN);
        }
        return categories;
    }
}
