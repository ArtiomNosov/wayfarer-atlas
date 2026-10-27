package org.wayfarer.atlas.domain;

import org.junit.Test;

public final class DomainTest {
    @Test
    public void verifiesTravelAndEntryRules() {
        DomainBehaviorChecks.runAll();
    }
}
