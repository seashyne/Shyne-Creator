package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AvatarStateDirtyRevisionTest {
    @Test
    void acknowledgingOlderSnapshotDoesNotEraseNewerMutation() {
        AvatarState state = state();
        long sentRevision = state.captureSnapshotRevision();
        state.markSnapshotDirty();

        state.acknowledgeSnapshotRevision(sentRevision);
        assertTrue(state.isSnapshotDirty());

        state.acknowledgeSnapshotRevision(state.captureSnapshotRevision());
        assertFalse(state.isSnapshotDirty());
    }

    @Test
    void eachNetworkLaneUsesIndependentRevisions() {
        AvatarState state = state();
        state.clearSnapshotDirty();
        state.markPoseDirty();
        state.markSyncedDirty();
        state.setAnimationParameter("speed", 1.0);

        long pose = state.capturePoseRevision();
        long synced = state.captureSyncedRevision();
        long parameters = state.captureAnimationParametersRevision();
        state.markPoseDirty();

        state.acknowledgePoseRevision(pose);
        state.acknowledgeSyncedRevision(synced);
        state.acknowledgeAnimationParametersRevision(parameters);

        assertTrue(state.isPoseDirty());
        assertFalse(state.isSyncedDirty());
        assertFalse(state.areAnimationParametersDirty());
    }

    private static AvatarState state() {
        return new AvatarState("revision-test", "avatar:revision-test", Path.of("revision-test"), true, Set.of(), Set.of());
    }
}
