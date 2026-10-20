package seashyne.shynecore.client.compat;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for replay detection and replay local player resolution.
 * การทดสอบ unit test สำหรับระบบตรวจจับ replay และการจับคู่ตัวละคร local ใน replay.
 */
final class ReplayCompatTest {

    @Test
    void testReplayActiveWhenNoClient() {
        // Without an active Minecraft client or replay server, isReplayActive should return false safely
        // เมื่อไม่มี Minecraft client หรือ replay server การตรวจจับต้องคืนค่า false อย่างปลอดภัย
        assertFalse(ReplayCompat.isReplayActive());
        assertFalse(ReplayCompat.isExporting());
    }

    @Test
    void testReplayLocalPlayerUuidWhenInactive() {
        UUID randomId = UUID.randomUUID();
        assertFalse(ReplayCompat.isReplayLocalPlayerUuid(randomId));
        assertFalse(ReplayCompat.isReplayLocalPlayerUuid(null));
    }

    @Test
    void testResolveAttachmentWithoutActiveAvatar() {
        UUID entityId = UUID.randomUUID();
        // Without an active avatar, resolving replay attachment should return null gracefully
        // เมื่อไม่มี avatar ที่เปิดใช้อยู่ การ resolve attachment ต้องคืนค่า null โดยไม่โยน exception
        assertNull(ReplayCompat.resolveReplayAttachment(entityId));
    }
}
