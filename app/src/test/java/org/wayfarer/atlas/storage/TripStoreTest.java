package org.wayfarer.atlas.storage;

import org.junit.Test;

public final class TripStoreTest {
    @Test public void persistsAndProtectsLocalTrips() throws Exception {
        StorageBehaviorChecks.runAll();
    }
}
