package seashyne.shynecore.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.render.AvatarRenderTaskRegistry;

import java.nio.file.Path;
import java.util.Locale;

/** Live in-game Studio profiler view of the costs attributable to the active Shyne Avatar. */
public final class AvatarProfilerScreen extends Screen {
    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private Path lastExport;

    public AvatarProfilerScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.profiler.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(620, this.width - 20);
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(8, (this.height - 340) / 2);

        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.profiler.export"), button -> export())
            .bounds(panelX + panelWidth - 314, panelY + 308, 98, 20));
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.profiler.reset"), button -> AvatarProfiler.reset())
            .bounds(panelX + panelWidth - 212, panelY + 308, 98, 20));
        addRenderableWidget(ShyneButton.primary(Component.translatable("gui.done"), button -> onClose())
            .bounds(panelX + panelWidth - 110, panelY + 308, 98, 20));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var tasks = AvatarRenderTaskRegistry.snapshots();
        var snapshot = AvatarProfiler.snapshot(tasks.size(), AvatarRenderTaskRegistry.estimatedBytes());

        ShyneTheme.drawBackdrop(graphics, this.width, this.height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, 340, 46);

        graphics.text(this.font, this.title, panelX + 16, panelY + 14, ShyneTheme.TEXT_PRIMARY, true);
        graphics.text(this.font, Component.literal(snapshot.avatarId().isBlank() ? "Minecraft Default" : snapshot.avatarId()), panelX + 16, panelY + 31, ShyneTheme.TEXT_SECONDARY, false);

        int summaryY = panelY + 54;
        value(graphics, "FPS", Integer.toString(snapshot.fps()), panelX + 16, summaryY, snapshot.fps() < 60 ? ShyneTheme.DANGER : ShyneTheme.SUCCESS);
        value(graphics, "Frame", ms(snapshot.frameMs()), panelX + 114, summaryY, ShyneTheme.TEXT_PRIMARY);
        value(graphics, "Avatar/frame", ms(snapshot.avatarFrameMs()), panelX + 224, summaryY, snapshot.avatarFrameMs() > 4 ? ShyneTheme.WARNING : ShyneTheme.TEXT_PRIMARY);
        value(graphics, "Est FPS loss", String.format(Locale.ROOT, "%.1f", snapshot.estimatedFpsLoss()), panelX + 344, summaryY, ShyneTheme.WARNING);
        value(graphics, "Memory", bytes(snapshot.avatarBytes()), panelX + 464, summaryY, ShyneTheme.TEXT_PRIMARY);

        var quota = snapshot.quota();
        value(graphics, "Dyn Textures", (quota != null ? quota.dynamicTextures() + "/" + quota.maxDynamicTextures() : "0/16"), panelX + 16, summaryY + 28, ShyneTheme.TEXT_PRIMARY);
        value(graphics, "Particles", (quota != null ? quota.particlesPerSecond() + "/s" : "0/s"), panelX + 129, summaryY + 28, (quota != null && quota.particlesDroppedPerSecond() > 0) ? ShyneTheme.DANGER : ShyneTheme.TEXT_PRIMARY);
        value(graphics, "Sounds", (quota != null ? quota.soundsPerSecond() + "/s" : "0/s"), panelX + 229, summaryY + 28, (quota != null && quota.soundsDroppedPerSecond() > 0) ? ShyneTheme.DANGER : ShyneTheme.TEXT_PRIMARY);
        value(graphics, "Tasks", AvatarRenderTaskRegistry.lastRendered() + "/" + snapshot.taskCount(), panelX + 324, summaryY + 28, AvatarRenderTaskRegistry.lastCulled() > 0 ? ShyneTheme.WARNING : ShyneTheme.TEXT_PRIMARY);
        int totalGeo = snapshot.geometryRendered() + snapshot.geometryCulled();
        String geoText = totalGeo > 0 ? snapshot.geometryRendered() + "/" + totalGeo : (snapshot.cubes() + "c");
        value(graphics, "Geometry", geoText, panelX + 444, summaryY + 28, snapshot.geometryCulled() > 0 ? ShyneTheme.WARNING : ShyneTheme.SUCCESS);

        graphics.text(this.font, Component.translatable("screen.shyne_core.profiler.metrics"), panelX + 16, panelY + 116, ShyneTheme.ACCENT_BRIGHT, false);
        int row = 0;
        for (AvatarProfiler.Category category : AvatarProfiler.Category.values()) {
            AvatarProfiler.Metric metric = snapshot.metrics().get(category);
            int y = panelY + 132 + row++ * 20;
            boolean hovered = mouseX >= panelX + 12 && mouseX < panelX + panelWidth - 12 && mouseY >= y - 3 && mouseY < y + 17;
            ShyneTheme.drawCard(graphics, panelX + 12, y - 3, panelWidth - 24, 19, hovered, false);

            graphics.text(this.font, Component.literal(label(category)), panelX + 18, y + 1, ShyneTheme.TEXT_PRIMARY, false);
            graphics.text(this.font, Component.literal("avg " + ms(metric.averageMs())), panelX + 210, y + 1, ShyneTheme.TEXT_SECONDARY, false);
            graphics.text(this.font, Component.literal("max " + ms(metric.maximumMs())), panelX + 330, y + 1, metric.maximumMs() > 8 ? ShyneTheme.DANGER : ShyneTheme.TEXT_SECONDARY, false);
            graphics.text(this.font, Component.literal("last " + ms(metric.lastMs())), panelX + 450, y + 1, ShyneTheme.TEXT_SECONDARY, false);
        }

        int warningY = panelY + 258;
        graphics.text(this.font, Component.translatable("screen.shyne_core.profiler.findings"), panelX + 16, warningY, ShyneTheme.ACCENT_BRIGHT, false);
        if (snapshot.warnings().isEmpty()) {
            graphics.text(this.font, Component.translatable("screen.shyne_core.profiler.healthy"), panelX + 16, warningY + 16, ShyneTheme.SUCCESS, false);
        } else {
            String findings = String.join(" · ", snapshot.warnings().stream()
                .map(value -> Component.translatable("screen.shyne_core.profiler.warning." + value).getString()).toList());
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(findings, panelWidth - 32)), panelX + 16, warningY + 16, ShyneTheme.DANGER, false);
        }
        if (lastExport != null) {
            String exported = Component.translatable("screen.shyne_core.profiler.exported", lastExport.getFileName().toString()).getString();
            graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(exported, Math.max(80, panelWidth - 338))), panelX + 16, panelY + 314, ShyneTheme.SUCCESS, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void export() {
        try {
            lastExport = AvatarProfiler.exportSnapshot(AvatarRenderTaskRegistry.snapshots().size(),
                AvatarRenderTaskRegistry.estimatedBytes(), AvatarRenderTaskRegistry.lastRendered(),
                AvatarRenderTaskRegistry.lastCulled());
        } catch (Exception ignored) {
            lastExport = null;
        }
    }

    private void value(GuiGraphicsExtractor graphics, String label, String value, int x, int y, int color) {
        graphics.text(this.font, Component.literal(label), x, y, ShyneTheme.TEXT_DIM, false);
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

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.3f ms", value);
    }

    private static String bytes(long value) {
        if (value >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0);
        if (value >= 1024L) return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0);
        return value + " B";
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
