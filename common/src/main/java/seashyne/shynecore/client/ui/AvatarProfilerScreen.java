package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.render.AvatarRenderTaskRegistry;

import java.util.Locale;

/** Live in-game view of the costs attributable to the active Shyne Avatar. */
public final class AvatarProfilerScreen extends Screen {
    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;

    public AvatarProfilerScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.profiler.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(600, this.width - 20);
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(8, (this.height - 340) / 2);
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.profiler.reset"), button -> AvatarProfiler.reset())
            .bounds(panelX + panelWidth - 212, panelY + 308, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(panelX + panelWidth - 110, panelY + 308, 98, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var tasks = AvatarRenderTaskRegistry.snapshots();
        var snapshot = AvatarProfiler.snapshot(tasks.size(), AvatarRenderTaskRegistry.estimatedBytes());
        graphics.fill(0, 0, this.width, this.height, 0xAA030918);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + 340, 0xF20A1630, 0xF2071026);
        graphics.outline(panelX, panelY, panelWidth, 340, 0xFF22D7E8);
        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.AQUA), panelX + 12, panelY + 10, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.literal(snapshot.avatarId().isBlank() ? "Minecraft Default" : snapshot.avatarId()), panelX + 12, panelY + 27, 0xFF91A7C6, false);

        int summaryY = panelY + 48;
        value(graphics, "FPS", Integer.toString(snapshot.fps()), panelX + 12, summaryY, snapshot.fps() < 60 ? 0xFFFF6B7A : 0xFF65F2B3);
        value(graphics, "Frame", ms(snapshot.frameMs()), panelX + 130, summaryY, 0xFFF3F7FF);
        value(graphics, "Avatar/frame", ms(snapshot.avatarFrameMs()), panelX + 260, summaryY, snapshot.avatarFrameMs() > 4 ? 0xFFFFB86B : 0xFFF3F7FF);
        value(graphics, "Estimated FPS loss", String.format(Locale.ROOT, "%.1f", snapshot.estimatedFpsLoss()), panelX + 420, summaryY, 0xFFFFB86B);
        value(graphics, "Avatar memory", bytes(snapshot.avatarBytes()), panelX + 12, summaryY + 28, 0xFFF3F7FF);
        value(graphics, "JVM heap", bytes(snapshot.heapBytes()), panelX + 180, summaryY + 28, 0xFFF3F7FF);
        value(graphics, "Tasks", Integer.toString(snapshot.taskCount()), panelX + 340, summaryY + 28, snapshot.taskCount() > 128 ? 0xFFFF6B7A : 0xFFF3F7FF);
        value(graphics, "Model", snapshot.bones() + " bones · " + snapshot.cubes() + " cubes", panelX + 430, summaryY + 28, 0xFFF3F7FF);

        graphics.text(this.font, Component.translatable("screen.shyne_core.profiler.metrics").withStyle(ChatFormatting.AQUA), panelX + 12, panelY + 112, 0xFFFFFFFF, false);
        int row = 0;
        for (AvatarProfiler.Category category : AvatarProfiler.Category.values()) {
            AvatarProfiler.Metric metric = snapshot.metrics().get(category);
            int y = panelY + 130 + row++ * 20;
            if (row % 2 == 0) graphics.fill(panelX + 8, y - 3, panelX + panelWidth - 8, y + 16, 0x2219BFD1);
            graphics.text(this.font, Component.literal(label(category)), panelX + 14, y, 0xFFF3F7FF, false);
            graphics.text(this.font, Component.literal("avg " + ms(metric.averageMs())), panelX + 210, y, 0xFF91A7C6, false);
            graphics.text(this.font, Component.literal("max " + ms(metric.maximumMs())), panelX + 330, y, metric.maximumMs() > 8 ? 0xFFFF6B7A : 0xFF91A7C6, false);
            graphics.text(this.font, Component.literal("last " + ms(metric.lastMs())), panelX + 450, y, 0xFF91A7C6, false);
        }

        int warningY = panelY + 258;
        graphics.text(this.font, Component.translatable("screen.shyne_core.profiler.findings").withStyle(ChatFormatting.AQUA), panelX + 12, warningY, 0xFFFFFFFF, false);
        if (snapshot.warnings().isEmpty()) {
            graphics.text(this.font, Component.translatable("screen.shyne_core.profiler.healthy"), panelX + 12, warningY + 17, 0xFF65F2B3, false);
        } else {
            String findings = String.join(" · ", snapshot.warnings().stream()
                .map(value -> Component.translatable("screen.shyne_core.profiler.warning." + value).getString()).toList());
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(findings, panelWidth - 24)), panelX + 12, warningY + 17, 0xFFFF6B7A, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void value(GuiGraphicsExtractor graphics, String label, String value, int x, int y, int color) {
        graphics.text(this.font, Component.literal(label), x, y, 0xFF7188A8, false);
        graphics.text(this.font, Component.literal(value), x, y + 11, color, false);
    }

    private static String label(AvatarProfiler.Category category) {
        return switch (category) {
            case LUA_LOAD -> "Lua load";
            case LUA_TICK -> "Lua tick";
            case LUA_RENDER -> "Lua render";
            case LUA_EVENT -> "Lua events";
            case MODEL_RENDER -> "Model render";
            case TASK_RENDER -> "Render tasks";
        };
    }

    private static String ms(double value) { return String.format(Locale.ROOT, "%.3f ms", value); }
    private static String bytes(long value) {
        if (value >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0);
        if (value >= 1024L) return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0);
        return value + " B";
    }

    @Override public void onClose() { if (this.minecraft != null) this.minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
