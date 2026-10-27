package org.wayfarer.atlas.data;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.wayfarer.atlas.domain.EntryRule;
import org.wayfarer.atlas.domain.EntryCategory;
import org.wayfarer.atlas.domain.RuleCitation;

public final class RuleMerger {
    private RuleMerger() {}
    public static List<EntryRule> combine(List<EntryRule> community, List<List<EntryRule>> official) {
        Map<String, EntryRule> combined = new LinkedHashMap<>();
        for (EntryRule rule : community) combined.put(key(rule), rule);
        Map<String, List<EntryRule>> authoritative = new LinkedHashMap<>();
        for (List<EntryRule> layer : official) for (EntryRule rule : layer) {
            authoritative.computeIfAbsent(key(rule), ignored -> new ArrayList<>()).add(rule);
        }
        for (Map.Entry<String, List<EntryRule>> entry : authoritative.entrySet()) {
            List<EntryRule> rows = entry.getValue();
            EntryRule preferred = rows.get(0);
            boolean conflict = false;
            Map<String, RuleCitation> citations = new LinkedHashMap<>();
            for (EntryRule row : rows) {
                if (row.getCategory() != preferred.getCategory()
                        || (row.getMaxStayDays() != null && preferred.getMaxStayDays() != null
                        && !Objects.equals(row.getMaxStayDays(), preferred.getMaxStayDays()))) conflict = true;
                if (preferred.getMaxStayDays() == null && row.getMaxStayDays() != null) preferred = row;
                for (RuleCitation citation : row.getCitations()) citations.putIfAbsent(citation.getUrl(), citation);
            }
            combined.put(entry.getKey(), new EntryRule(preferred.getCitizenship(), preferred.getDestination(),
                    conflict ? EntryCategory.UNKNOWN : preferred.getCategory(), conflict ? null : preferred.getMaxStayDays(),
                    conflict ? "Official sources disagree on the entry category or stay limit. Check every cited source before making travel plans." : preferred.getNotes(),
                    preferred.getSourceUrl(), conflict ? null : preferred.getCheckedAt(),
                    conflict ? "Официальные источники противоречат друг другу в условиях въезда или сроке пребывания. Уточните сведения по каждой ссылке перед принятием решения." : preferred.getNotesRu(),
                    new ArrayList<>(citations.values())));
        }
        return new ArrayList<>(combined.values());
    }
    private static String key(EntryRule rule) { return rule.getCitizenship() + ":" + rule.getDestination(); }
}
