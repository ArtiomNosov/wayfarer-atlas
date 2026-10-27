package org.wayfarer.atlas.data;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.wayfarer.atlas.domain.EntryCategory;
import org.wayfarer.atlas.domain.EntryRule;
import org.wayfarer.atlas.domain.RuleCitation;

public final class MultiSourceTest {
    private static EntryRule official(String source, EntryCategory category, Integer days) {
        return new EntryRule("RU", "HK", category, days, "Ordinary passport visitor.", source, "2026-10-26",
            "Краткосрочный визит по обычному загранпаспорту.",
            List.of(new RuleCitation("Immigration authority", "Иммиграционное ведомство", source, "2026-10-26", true)));
    }

    @Test public void officialRussianConditionsSurviveDatasetParsing() {
        String json = "{\"version\":\"1\",\"source\":\"https://www.immd.gov.hk/\",\"rules\":[{\"citizenship\":\"RU\",\"destination\":\"HK\",\"category\":\"VISA_FREE\",\"notes\":\"Ordinary passport\",\"notesRu\":\"Обычный загранпаспорт\"}]}";
        EntryRule rule = DatasetParser.parseRules(json, java.util.Set.of("RU", "HK")).getRules().get(0);
        assertEquals("Обычный загранпаспорт", rule.getNotesRu());
    }

    @Test public void officialSupplementFillsMissingDestination() {
        List<EntryRule> result = RuleMerger.combine(List.of(), List.of(List.of(official("https://www.immd.gov.hk/", EntryCategory.VISA_FREE, 14))));
        assertEquals(1, result.size());
        assertEquals(EntryCategory.VISA_FREE, result.get(0).getCategory());
        assertEquals(Integer.valueOf(14), result.get(0).getMaxStayDays());
        assertTrue(result.get(0).getCitations().get(0).isOfficial());
    }

    @Test public void refreshedCommunityCannotEraseOfficialSupplement() {
        EntryRule community = new EntryRule("RU", "HK", EntryCategory.VISA_REQUIRED, null, "", "https://example.org/community", "2026-11-01");
        List<EntryRule> result = RuleMerger.combine(List.of(community), List.of(List.of(official("https://www.immd.gov.hk/", EntryCategory.VISA_FREE, 14))));
        assertEquals(1, result.size());
        assertEquals(EntryCategory.VISA_FREE, result.get(0).getCategory());
        assertEquals("https://www.immd.gov.hk/", result.get(0).getSourceUrl());
    }

    @Test public void disagreeingOfficialCategoriesStayUnknownWithBothCitations() {
        List<EntryRule> result = RuleMerger.combine(List.of(), List.of(
            List.of(official("https://authority.example/a", EntryCategory.VISA_FREE, 14)),
            List.of(official("https://authority.example/b", EntryCategory.VISA_REQUIRED, null))));
        assertEquals(1, result.size());
        assertEquals(EntryCategory.UNKNOWN, result.get(0).getCategory());
        assertNull(result.get(0).getMaxStayDays());
        assertEquals(2, result.get(0).getCitations().size());
        assertTrue(result.get(0).getNotesRu().contains("противореч"));
    }

    @Test public void differentOfficialStayLimitsAreNotSilentlyChosen() {
        List<EntryRule> result = RuleMerger.combine(List.of(), List.of(
            List.of(official("https://authority.example/a", EntryCategory.VISA_FREE, 14)),
            List.of(official("https://authority.example/b", EntryCategory.VISA_FREE, 30))));
        assertEquals(1, result.size());
        assertEquals(EntryCategory.UNKNOWN, result.get(0).getCategory());
    }

    @Test public void agreeingOfficialSourcesRetainBothCitationsAndRussianNotes() {
        List<EntryRule> result = RuleMerger.combine(List.of(), List.of(
            List.of(official("https://authority.example/a", EntryCategory.VISA_FREE, 14)),
            List.of(official("https://authority.example/b", EntryCategory.VISA_FREE, 14))));
        assertEquals(1, result.size());
        assertEquals(EntryCategory.VISA_FREE, result.get(0).getCategory());
        assertEquals(2, result.get(0).getCitations().size());
        assertFalse(result.get(0).getNotesRu().isEmpty());
    }

    @Test public void unrelatedCommunityRowsRemainAvailableAndInputsUnchanged() {
        EntryRule original = new EntryRule("RU", "FR", EntryCategory.VISA_REQUIRED);
        List<EntryRule> base = List.of(original);
        List<EntryRule> result = RuleMerger.combine(base, List.of(List.of(official("https://www.immd.gov.hk/", EntryCategory.VISA_FREE, 14))));
        assertEquals(2, result.size());
        assertSame(original, result.get(0));
        assertEquals(1, base.size());
    }
}
