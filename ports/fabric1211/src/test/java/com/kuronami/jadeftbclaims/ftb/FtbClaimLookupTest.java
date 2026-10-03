package com.kuronami.jadeftbclaims.ftb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FtbClaimLookupTest {

    @AfterEach
    void resetGuard() {
        FtbClaimLookup.resetForTest();
    }

    @Test
    void returnsEmptyForMissingSyncedDataWithoutDisablingLookup() {
        FtbClaimLookup.resetForTest();

        assertEquals(Optional.empty(), FtbClaimLookup.guard(Optional::empty));
        assertFalse(FtbClaimLookup.isDisabled());
    }

    @Test
    void disablesLookupAfterLinkageFailureAndDoesNotRetry() {
        FtbClaimLookup.resetForTest();
        AtomicInteger calls = new AtomicInteger();

        assertEquals(Optional.empty(), FtbClaimLookup.guard(() -> {
            calls.incrementAndGet();
            throw new NoSuchMethodError("fixture API drift");
        }));
        assertTrue(FtbClaimLookup.isDisabled());
        assertEquals(Optional.empty(), FtbClaimLookup.guard(() -> {
            calls.incrementAndGet();
            return Optional.of("must not be observed");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void returnsClaimInfoWhenLookupSucceeds() {
        FtbClaimLookup.resetForTest();
        ClaimInfo expected = new ClaimInfo(null, true);

        assertEquals(Optional.of(expected), FtbClaimLookup.guard(() -> Optional.of(expected)));
        assertFalse(FtbClaimLookup.isDisabled());
    }
}
