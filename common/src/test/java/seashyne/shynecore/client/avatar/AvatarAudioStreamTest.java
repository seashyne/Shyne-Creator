package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import seashyne.shynecore.client.avatar.sound.AvatarAudioStream;
import seashyne.shynecore.client.avatar.sound.AvatarAudioStreamManager;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarAudioStreamTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        AvatarAudioStreamManager.clear();
    }

    @Test
    void streamCreationClampsVolumeAndPitch() {
        AvatarAudioStream stream = new AvatarAudioStream(1, "https://example.com/audio.ogg", "avatar1", -5.0f, 10.0f, false, null, null, null);
        assertEquals(0.0f, stream.currentLevel());
        assertFalse(stream.isBeat());
        assertFalse(stream.isPlaying());
        assertEquals("avatar1", stream.avatarId());
        assertEquals("https://example.com/audio.ogg", stream.url());
    }

    @Test
    void managerRejectsInvalidUrls() {
        int id1 = AvatarAudioStreamManager.createStream("file:///etc/passwd", "avatar1", 1f, 1f, false, null, null, null, false);
        assertEquals(0, id1, "Local file scheme should be rejected");

        int id2 = AvatarAudioStreamManager.createStream("", "avatar1", 1f, 1f, false, null, null, null, false);
        assertEquals(0, id2, "Empty URL should be rejected");

        int id3 = AvatarAudioStreamManager.createStream("javascript:alert(1)", "avatar1", 1f, 1f, false, null, null, null, false);
        assertEquals(0, id3, "Javascript URL should be rejected");
    }

    @Test
    void managerAcceptsValidHttpUrls() {
        int id = AvatarAudioStreamManager.createStream("https://example.com/song.ogg", "avatar1", 0.8f, 1.0f, true, 10.0, 64.0, -5.0, false);
        assertTrue(id > 0, "Valid HTTPS URL should be accepted");

        AvatarAudioStream stream = AvatarAudioStreamManager.get(id);
        assertNotNull(stream);
        assertEquals(id, stream.id());
        assertEquals(AvatarAudioStream.State.INIT, stream.state());

        AvatarAudioStreamManager.stopAllFor("avatar1");
        assertTrue(stream.isStopped());
    }
}
