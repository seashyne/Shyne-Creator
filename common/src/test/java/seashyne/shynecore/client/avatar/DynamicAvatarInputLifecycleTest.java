package seashyne.shynecore.client.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the lease behavior used while an Avatar replacement is staged and rolled back. */
class DynamicAvatarInputLifecycleTest {
    @Test
    void failedReplacementKeepsPreviousRuntimeLease() {
        InputOwnerSet owners = new InputOwnerSet();
        Object previousRuntime = new Object();
        Object stagedRuntime = new Object();

        owners.acquire(previousRuntime);
        owners.acquire(stagedRuntime);
        assertEquals(2, owners.size());

        assertTrue(owners.release(stagedRuntime));
        assertFalse(owners.isEmpty(), "rollback must keep the previous Avatar binding active");
        assertTrue(owners.release(previousRuntime));
        assertTrue(owners.isEmpty());
    }

    @Test
    void registeringSameBindingTwiceForOneRuntimeIsIdempotent() {
        InputOwnerSet owners = new InputOwnerSet();
        Object runtime = new Object();
        owners.acquire(runtime);
        owners.acquire(runtime);
        assertEquals(1, owners.size());
    }
}
