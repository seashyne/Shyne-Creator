package seashyne.shynecore.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RemotePlayerPolicyTest {
    @Test
    void combinesHideAndNameplateChoicesWithoutBlocking() {
        String policy = RemotePlayerPolicy.with(RemotePlayerPolicy.VISIBLE, RemotePlayerPolicy.HIDDEN, true);
        policy = RemotePlayerPolicy.with(policy, RemotePlayerPolicy.MUTED, true);

        assertEquals("hidden,muted", policy);
        assertTrue(RemotePlayerPolicy.isHidden(policy, false, false));
        assertTrue(RemotePlayerPolicy.isMuted(policy));
        assertFalse(RemotePlayerPolicy.shouldLoad(policy, false, false));
        assertFalse(RemotePlayerPolicy.has(policy, RemotePlayerPolicy.BLOCKED));
    }

    @Test
    void blockSupersedesOtherChoices() {
        String policy = RemotePlayerPolicy.with("hidden,muted", RemotePlayerPolicy.BLOCKED, true);

        assertEquals(RemotePlayerPolicy.BLOCKED, policy);
        assertTrue(RemotePlayerPolicy.isHidden(policy, false, false));
        assertTrue(RemotePlayerPolicy.isMuted(policy));
        assertFalse(RemotePlayerPolicy.shouldLoad(policy, false, false));
    }

    @Test
    void hiddenAndBlockedPoliciesStopRemoteAvatarLoading() {
        assertFalse(RemotePlayerPolicy.shouldLoad(RemotePlayerPolicy.HIDDEN, false, false));
        assertFalse(RemotePlayerPolicy.shouldLoad(RemotePlayerPolicy.BLOCKED, false, false));
        assertTrue(RemotePlayerPolicy.shouldLoad(RemotePlayerPolicy.MUTED, false, false));
    }

    @Test
    void globalAndUnratedChoicesPreventLoadingWithoutBecomingBlock() {
        assertTrue(RemotePlayerPolicy.isHidden(RemotePlayerPolicy.VISIBLE, true, false));
        assertFalse(RemotePlayerPolicy.shouldLoad(RemotePlayerPolicy.VISIBLE, true, false));
        assertTrue(RemotePlayerPolicy.isHidden(RemotePlayerPolicy.VISIBLE, false, true));
        assertFalse(RemotePlayerPolicy.shouldLoad(RemotePlayerPolicy.VISIBLE, false, true));
        assertFalse(RemotePlayerPolicy.has(RemotePlayerPolicy.VISIBLE, RemotePlayerPolicy.BLOCKED));
    }

    @Test
    void normalizesLegacyCombinedEncoding() {
        assertEquals("hidden,muted", RemotePlayerPolicy.normalize("muted+hidden,unknown"));
        assertEquals(RemotePlayerPolicy.BLOCKED, RemotePlayerPolicy.normalize("hidden,blocked,muted"));
    }
}
