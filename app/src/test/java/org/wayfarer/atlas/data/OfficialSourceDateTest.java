package org.wayfarer.atlas.data;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.wayfarer.atlas.domain.EntryRule;
import org.wayfarer.atlas.domain.EntryCategory;
public final class OfficialSourceDateTest {
    private RuleSnapshot snapshot(String asAt) {
        return new RuleSnapshot(List.of(new EntryRule("RU","HK",EntryCategory.VISA_FREE)),"https://www.immd.gov.hk/","2026-10-26",asAt);
    }
    @Test public void newDownloadCannotHideOlderOfficialStateDate() {
        assertThrows(IllegalArgumentException.class,()->ReferenceUpdatePolicy.validateReplacement(snapshot("2025-12-31"),snapshot("2024-12-31"),1,1,1));
    }
    @Test public void knownOfficialStateDateCannotDisappearDuringRefresh() {
        assertThrows(IllegalArgumentException.class,()->ReferenceUpdatePolicy.validateReplacement(snapshot("2025-12-31"),snapshot(null),1,1,1));
    }
    @Test public void newerOfficialStateDateCanReplacePreviousState() {
        ReferenceUpdatePolicy.validateReplacement(snapshot("2025-12-31"),snapshot("2026-01-31"),1,1,1);
    }
}
