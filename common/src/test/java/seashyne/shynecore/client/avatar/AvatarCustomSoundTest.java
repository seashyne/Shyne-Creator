package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seashyne.shynecore.client.avatar.sound.AvatarCustomSoundManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarCustomSoundTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesSoundFilesCorrectly() throws IOException {
        Path soundsDir = tempDir.resolve("sounds");
        Files.createDirectories(soundsDir);

        Path roarFile = soundsDir.resolve("my_roar.ogg");
        Files.writeString(roarFile, "fake ogg content");

        Path jumpFile = soundsDir.resolve("Jump_Sound.ogg");
        Files.writeString(jumpFile, "fake ogg content");

        Path rootLaser = tempDir.resolve("laser.ogg");
        Files.writeString(rootLaser, "fake ogg content");

        // 1. By simple name (my_roar -> sounds/my_roar.ogg)
        Path found1 = AvatarCustomSoundManager.findSoundFile(tempDir, "my_roar");
        assertNotNull(found1);
        assertEquals(roarFile.toAbsolutePath().normalize(), found1.toAbsolutePath().normalize());

        // 2. With .ogg extension (my_roar.ogg)
        Path found2 = AvatarCustomSoundManager.findSoundFile(tempDir, "my_roar.ogg");
        assertNotNull(found2);
        assertEquals(roarFile.toAbsolutePath().normalize(), found2.toAbsolutePath().normalize());

        // 3. With sounds/ prefix (sounds/my_roar)
        Path found3 = AvatarCustomSoundManager.findSoundFile(tempDir, "sounds/my_roar");
        assertNotNull(found3);
        assertEquals(roarFile.toAbsolutePath().normalize(), found3.toAbsolutePath().normalize());

        // 4. Case-insensitive lookup (jump_sound -> Jump_Sound.ogg)
        Path found4 = AvatarCustomSoundManager.findSoundFile(tempDir, "jump_sound");
        assertNotNull(found4);
        assertEquals(jumpFile.toAbsolutePath().normalize(), found4.toAbsolutePath().normalize());

        // 5. Root file (laser)
        Path found5 = AvatarCustomSoundManager.findSoundFile(tempDir, "laser");
        assertNotNull(found5);
        assertEquals(rootLaser.toAbsolutePath().normalize(), found5.toAbsolutePath().normalize());

        // 6. Non-existent file
        assertNull(AvatarCustomSoundManager.findSoundFile(tempDir, "ghost_sound"));

        // 7. Path traversal prevention
        assertNull(AvatarCustomSoundManager.findSoundFile(tempDir, "../outside"));
        assertNull(AvatarCustomSoundManager.findSoundFile(tempDir, "../../secret"));
    }
}
