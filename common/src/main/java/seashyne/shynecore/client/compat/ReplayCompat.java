package seashyne.shynecore.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Compatibility bridge for Minecraft replay systems (such as Flashback and ReplayMod).
 * Provides environment detection, dummy entity resolution, and avatar state binding
 * during replay playback and video export.
 *
 * <p>Execution Context: Client Thread / Render Thread.</p>
 * <p>Thread Safety: Accesses Minecraft client world and entities; must be called from the client thread.</p>
 *
 * สะพานเชื่อมความเข้ากันได้สำหรับระบบ Replay ของ Minecraft (เช่น Flashback และ ReplayMod).
 * ทำหน้าที่ตรวจจับสภาพแวดล้อม replay, จับคู่ dummy entity กับอวตารของผู้เล่น,
 * และผูก state โมเดลขณะเปิดดูหรือ export วิดีโอ.
 *
 * <p>บริบทการทำงาน: Client Thread / Render Thread.</p>
 * <p>ความปลอดภัยของ Thread: เข้าถึง client world และ entity ต้องเรียกจาก client thread.</p>
 */
public final class ReplayCompat {
    private static Boolean cachedFlashbackAvailable = null;

    private ReplayCompat() {}

    /**
     * Checks if the client is currently viewing or rendering a replay.
     * ตรวจสอบว่า client กำลังเปิดดูหรือเรนเดอร์ replay อยู่หรือไม่.
     *
     * @return true if a replay session or replay server is active.
     */
    public static boolean isReplayActive() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;

        // 1. Check if singleplayer server is a ReplayServer (Flashback / ReplayMod)
        // ตรวจสอบว่า Singleplayer server เป็น ReplayServer หรือไม่
        var server = client.getSingleplayerServer();
        if (server != null && server.getClass().getSimpleName().contains("ReplayServer")) {
            return true;
        }

        // 2. Flashback static reflection check
        // ตรวจสอบผ่าน static method ของ Flashback โดยใช้ reflection ปลอดภัย
        if (isFlashbackReplayActive()) {
            return true;
        }

        // 3. Screen check for replay viewer UI
        // ตรวจสอบหน้าจอ UI ว่าเป็นหน้าจอของ Replay หรือไม่
        if (client.gui != null && client.gui.screen() != null) {
            String screenClass = client.gui.screen().getClass().getName();
            if (screenClass.contains("flashback") || screenClass.contains("replaymod")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if Flashback reports an active replay via reflection.
     * ตรวจสอบสถานะ replay ของ Flashback ผ่าน reflection.
     */
    private static boolean isFlashbackReplayActive() {
        if (Boolean.FALSE.equals(cachedFlashbackAvailable)) return false;
        try {
            Class<?> fb = Class.forName("com.moulberry.flashback.Flashback");
            cachedFlashbackAvailable = true;
            Method m = fb.getMethod("isInReplay");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (ClassNotFoundException e) {
            cachedFlashbackAvailable = false;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Checks if Flashback is currently performing an offline video export job.
     * ตรวจสอบว่า Flashback กำลังอยู่ในกระบวนการ Export วิดีโอหรือไม่.
     */
    public static boolean isExporting() {
        if (Boolean.FALSE.equals(cachedFlashbackAvailable)) return false;
        try {
            Class<?> fb = Class.forName("com.moulberry.flashback.Flashback");
            Method m = fb.getMethod("isExporting");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Finds the recorded local player entity inside the replay world.
     * ค้นหา entity ของผู้เล่น local ที่ถูกบันทึกไว้ในโลก replay.
     *
     * @param client Minecraft instance.
     * @return The recorded local player entity, or null if not found.
     */
    public static Player findReplayLocalPlayer(Minecraft client) {
        if (client == null || client.level == null) return null;

        UUID accountUuid = client.getUser() != null ? client.getUser().getProfileId() : null;
        String accountName = client.getUser() != null ? client.getUser().getName() : null;
        AvatarState active = AvatarRuntime.active();

        Player singleCandidate = null;
        int candidateCount = 0;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Player player) {
                // Ignore the spectator camera entity in replay
                // ข้าม entity กล้อง spectator ใน replay
                if (client.player != null && entity.getUUID().equals(client.player.getUUID())) {
                    continue;
                }

                // Match 1: Exact UUID match with client user account
                // ตรงกันด้วย UUID บัญชีของผู้เล่น
                if (accountUuid != null && accountUuid.equals(player.getUUID())) {
                    return player;
                }

                // Match 2: Exact username match with client user profile
                // ตรงกันด้วยชื่อบัญชีของผู้เล่น
                if (accountName != null && accountName.equalsIgnoreCase(player.getName().getString())) {
                    return player;
                }

                // Match 3: Bound entity ID on active avatar
                // ตรงกับ bound entity ID บน avatar ที่เปิดใช้อยู่
                if (active != null && player.getUUID().equals(active.boundEntityId())) {
                    return player;
                }

                singleCandidate = player;
                candidateCount++;
            }
        }

        // Match 4: If there is exactly one recorded human player in the replay level
        // หากมีตัวละครผู้เล่นคนเดียวในฉาก replay (เช่น การอัด Singleplayer หรือเดี่ยว)
        return candidateCount == 1 ? singleCandidate : null;
    }

    /**
     * Determines whether the given entity is the recorded local player in a replay session.
     * ตรวจสอบว่า entity ที่ระบุเป็นตัวละครของผู้เล่น local ใน session replay หรือไม่.
     *
     * @param entity Entity to inspect.
     * @return true if the entity represents the local player in a replay.
     */
    public static boolean isReplayLocalPlayer(Entity entity) {
        if (!isReplayActive()) return false;
        if (!(entity instanceof Player player)) return false;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;

        // The camera itself is never the rendered avatar player
        // ตัวกล้อง spectator ไม่ใช่อวตารที่ต้องเรนเดอร์
        if (client.player != null && entity.getUUID().equals(client.player.getUUID())) {
            return false;
        }

        UUID accountUuid = client.getUser() != null ? client.getUser().getProfileId() : null;
        if (accountUuid != null && accountUuid.equals(player.getUUID())) {
            return true;
        }

        String accountName = client.getUser() != null ? client.getUser().getName() : null;
        if (accountName != null && accountName.equalsIgnoreCase(player.getName().getString())) {
            return true;
        }

        AvatarState active = AvatarRuntime.active();
        if (active != null && player.getUUID().equals(active.boundEntityId())) {
            return true;
        }

        Player candidate = findReplayLocalPlayer(client);
        return candidate != null && candidate.equals(player);
    }

    /**
     * Checks if a given UUID belongs to the replay local player.
     * ตรวจสอบว่า UUID ที่กำหนดเป็นของตัวละคร local ใน replay หรือไม่.
     *
     * @param playerId UUID to test.
     * @return true if the UUID matches the local replay player.
     */
    public static boolean isReplayLocalPlayerUuid(UUID playerId) {
        if (!isReplayActive() || playerId == null) return false;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;

        // Camera spectator is ignored
        // ไม่นับกล้อง spectator
        if (client.player != null && playerId.equals(client.player.getUUID())) {
            return false;
        }

        UUID accountUuid = client.getUser() != null ? client.getUser().getProfileId() : null;
        if (accountUuid != null && accountUuid.equals(playerId)) {
            return true;
        }

        AvatarState active = AvatarRuntime.active();
        if (active != null && playerId.equals(active.boundEntityId())) {
            return true;
        }

        Player local = findReplayLocalPlayer(client);
        return local != null && playerId.equals(local.getUUID());
    }

    /**
     * Creates an active model attachment description for a replay player.
     * สร้าง AttachedModelState สำหรับตัวละครใน replay จากอวตารปัจจุบัน.
     *
     * @param entityId Entity UUID in the replay world.
     * @return AttachedModelState or null if no avatar is active.
     */
    public static AttachedModelState resolveReplayAttachment(UUID entityId) {
        AvatarState active = AvatarRuntime.active();
        if (active == null) return null;
        String modelId = active.modelId();
        if (modelId == null || modelId.isBlank()) return null;

        return new AttachedModelState(
            entityId,
            active.avatarId() != null ? active.avatarId() : "ReplayPlayer",
            modelId,
            0f, 0f, 0f, 1f, "", true
        );
    }
}
