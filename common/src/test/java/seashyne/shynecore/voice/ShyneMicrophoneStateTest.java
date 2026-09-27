package seashyne.shynecore.voice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ShyneMicrophoneStateTest {

    @BeforeEach
    void setUp() {
        ShyneMicrophoneState.clearAll();
        ShyneMicrophoneState.setDisabled(false);
        ShyneMicrophoneState.setConnected(true);
        ShyneMicrophoneState.setMuted(false);
    }

    @Test
    void testLocalAudioCalculation() {
        short[] loudSamples = new short[100];
        for (int i = 0; i < loudSamples.length; i++) {
            loudSamples[i] = 16000;
        }

        ShyneMicrophoneState.acceptAudio(loudSamples, false);
        ShyneMicrophoneState.Snapshot snapshot = ShyneMicrophoneState.snapshot();

        assertTrue(snapshot.available());
        assertTrue(snapshot.speaking());
        assertFalse(snapshot.muted());
        assertTrue(snapshot.level() > 0.1D);
    }

    @Test
    void testRemotePeerAudioCalculation() {
        UUID friendUuid = UUID.randomUUID();
        short[] pcm = new short[200];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = 20000;
        }

        // Before remote audio
        ShyneMicrophoneState.Snapshot before = ShyneMicrophoneState.getSpeakerSnapshot(friendUuid);
        assertFalse(before.speaking());

        // Feed remote audio for friend
        ShyneMicrophoneState.acceptRemoteAudio(friendUuid, pcm, false);

        ShyneMicrophoneState.Snapshot after = ShyneMicrophoneState.getSpeakerSnapshot(friendUuid);
        assertTrue(after.available());
        assertTrue(after.speaking());
        assertTrue(after.level() > 0.2D);
    }
}
