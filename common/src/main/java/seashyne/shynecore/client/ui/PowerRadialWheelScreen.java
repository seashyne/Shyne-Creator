package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.state.ClientPowerConfig;
import seashyne.shynecore.power.PowerState;
import seashyne.shynecore.profile.PlayerProfile;
import seashyne.shynecore.skill.SkillDefinition;
import seashyne.shynecore.skill.SkillSlot;

import java.util.*;

/**
 * Modern 6-slot quick-cast radial wheel for equipped abilities.
 */
public class PowerRadialWheelScreen extends Screen {
    private static final int SLOTS = 6;
    private static final int BG_COLOR = 0xC009101C;
    private static final int CARD_BG = 0xD8121E32;
    private static final int CARD_HOVER = 0xEE1E3557;
    private static final int ACCENT_CYAN = 0xFF3DD9E8;
    private static final int COLOR_BANNED = 0xFFFF5C5C;
    private static final int TEXT_MUTED = 0xFF8FA0B5;

    private int hoveredSlot = -1;

    public PowerRadialWheelScreen() {
        super(Component.translatable("screen.shyne_core.power_wheel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = wheelRadius();

        updateHoveredSlot(cx, cy, radius, mouseX, mouseY);
        drawCenterHub(graphics, cx, cy);
        drawSlices(graphics, cx, cy, radius);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int wheelRadius() {
        return Math.min(110, Math.max(60, (this.height - 80) / 2));
    }

    private void updateHoveredSlot(int cx, int cy, int radius, int mouseX, int mouseY) {
        hoveredSlot = -1;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 32 || dist > radius + 45) return;

        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        if (angle < 0) angle += 2 * Math.PI;
        double sliceAngle = (2 * Math.PI) / SLOTS;
        hoveredSlot = (int) Math.floor((angle + sliceAngle / 2.0) / sliceAngle) % SLOTS;
    }

    private void drawCenterHub(GuiGraphicsExtractor graphics, int cx, int cy) {
        int hubR = 34;
        graphics.fill(cx - hubR, cy - hubR, cx + hubR, cy + hubR, BG_COLOR);
        graphics.outline(cx - hubR, cy - hubR, hubR * 2, hubR * 2, ACCENT_CYAN);

        Minecraft mc = Minecraft.getInstance();
        graphics.text(this.font, Component.literal("✦ ABILITY"), cx - this.font.width("✦ ABILITY") / 2, cy - 10, ACCENT_CYAN, true);
        PowerState state = PowerUiVisibility.activeManaState(mc);
        if (state != null) {
            String manaStr = String.format(Locale.ROOT, "%.0f MP", state.mana());
            graphics.text(this.font, Component.literal(manaStr), cx - this.font.width(manaStr) / 2, cy + 2, 0xFFFFFFFF, false);
        }
    }

    private void drawSlices(GuiGraphicsExtractor graphics, int cx, int cy, int radius) {
        Minecraft mc = Minecraft.getInstance();
        PlayerProfile profile = mc.player != null ? ClientAnimationState.getProfile(mc.player.getUUID()) : null;
        Map<String, String> equipped = profile != null ? profile.equippedSkills() : Map.of();
        SkillSlot[] slots = SkillSlot.values();

        for (int i = 0; i < SLOTS; i++) {
            SkillSlot slot = i < slots.length ? slots[i] : SkillSlot.PRIMARY;
            double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / SLOTS);
            int sx = cx + (int) (Math.cos(angle) * radius);
            int sy = cy + (int) (Math.sin(angle) * radius);

            boolean isHovered = (i == hoveredSlot);
            String equippedSkillId = equipped.getOrDefault(slot.name().toLowerCase(Locale.ROOT), "");
            SkillDefinition skill = !equippedSkillId.isBlank() ? ClientAnimationState.getSkill(equippedSkillId) : null;
            boolean isBanned = skill != null && ClientPowerConfig.isBanned(skill.skillId());

            int boxW = 80;
            int boxH = 34;
            int left = sx - boxW / 2;
            int top = sy - boxH / 2;

            int bg = isHovered ? CARD_HOVER : CARD_BG;
            graphics.fill(left, top, left + boxW, top + boxH, bg);
            graphics.outline(left, top, boxW, boxH, isBanned ? COLOR_BANNED : (isHovered ? ACCENT_CYAN : 0xFF2A3D58));

            // Slot label
            graphics.text(this.font, Component.literal(slot.name()), left + 4, top + 4, isHovered ? ACCENT_CYAN : TEXT_MUTED, true);

            // Skill name or Empty
            String name = skill != null ? skill.displayName() : "— Empty —";
            String clipped = this.font.plainSubstrByWidth(name, boxW - 8);
            int nameColor = isBanned ? COLOR_BANNED : (skill != null ? 0xFFFFFFFF : 0xFF657891);
            graphics.text(this.font, Component.literal(clipped), left + 4, top + 18, nameColor, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            castHoveredSlot();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) { // ESC
            this.onClose();
            return true;
        }
        // Keys 1-6 trigger slots directly
        if (key >= 49 && key <= 54) {
            hoveredSlot = key - 49;
            castHoveredSlot();
            return true;
        }
        return super.keyPressed(event);
    }

    private void castHoveredSlot() {
        if (hoveredSlot < 0 || hoveredSlot >= SLOTS) return;
        SkillSlot[] slots = SkillSlot.values();
        SkillSlot slot = slots[hoveredSlot];
        Minecraft mc = Minecraft.getInstance();
        PlayerProfile profile = mc.player != null ? ClientAnimationState.getProfile(mc.player.getUUID()) : null;
        String equippedSkill = profile != null ? profile.equippedSkills().getOrDefault(slot.name().toLowerCase(Locale.ROOT), "") : "";

        if (!equippedSkill.isBlank() && !ClientPowerConfig.isBanned(equippedSkill)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            ShyneClientNetworking.sendSkillKey(slot.name().toLowerCase(Locale.ROOT), hoveredSlot + 1);
            this.onClose();
        }
    }
}
