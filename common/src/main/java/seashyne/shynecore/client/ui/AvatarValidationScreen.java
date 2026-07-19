package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarValidationReport;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class AvatarValidationScreen extends Screen {
    private static final int BACKGROUND = 0xF20B1222;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int OK = 0xFF7DDBB3;
    private static final int WARNING = 0xFFFFC66D;
    private static final int ERROR = 0xFFFF8793;
    private static final int MUTED = 0xFF91A0B7;

    private final Screen parent;
    private final int requestedPage;
    private final boolean refreshOnOpen;
    private boolean refreshRequested;
    private List<Line> lines = List.of();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int pageSize;
    private int pages;

    public AvatarValidationScreen(Screen parent) { this(parent, 0, true); }
    private AvatarValidationScreen(Screen parent, int page, boolean refreshOnOpen) {
        super(Component.translatable("screen.shyne_core.validation.title"));
        this.parent = parent;
        this.requestedPage = Math.max(0, page);
        this.refreshOnOpen = refreshOnOpen;
    }

    @Override
    protected void init() {
        lines = buildLines(AvatarRuntime.catalog());
        panelWidth = Math.min(760, Math.max(300, width - 20));
        panelHeight = Math.min(420, Math.max(220, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        pageSize = Math.max(6, (panelHeight - 100) / 12);
        pages = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        page = Math.min(requestedPage, pages - 1);

        int footerY = panelY + panelHeight - 30;
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> openPage(page - 1))
            .bounds(panelX + 14, footerY, 30, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("›"), button -> openPage(page + 1))
            .bounds(panelX + 48, footerY, 30, 20).build()).active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.validation.rescan"), button -> rescan())
            .bounds(panelX + panelWidth - 242, footerY, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.validation.folder"), button -> openFolder())
            .bounds(panelX + panelWidth - 160, footerY, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(panelX + panelWidth - 84, footerY, 70, 20).build());

        if (refreshOnOpen && !refreshRequested) {
            refreshRequested = true;
            var client = net.minecraft.client.Minecraft.getInstance();
            AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, error) -> client.execute(() -> {
                if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
            }));
        }
    }

    private static List<Line> buildLines(List<AvatarCatalogEntry> catalog) {
        List<Line> result = new ArrayList<>();
        if (catalog.isEmpty()) {
            result.add(new Line(Component.translatable("screen.shyne_core.validation.empty"), MUTED));
            return List.copyOf(result);
        }
        for (AvatarCatalogEntry entry : catalog) {
            AvatarValidationReport report = entry.validation();
            int color = entry.valid() ? report.warningCount() > 0 ? WARNING : OK : ERROR;
            String status = entry.valid() ? report.warningCount() > 0 ? "WARNING" : "OK" : "ERROR";
            result.add(new Line(Component.literal(status + "  " + entry.name() + "  [" + entry.id() + "]"), color));
            var stats = report.stats();
            result.add(new Line(Component.literal("  " + stats.bones() + " bones  •  " + stats.cubes() + " cubes  •  "
                + stats.animations() + " animations  •  " + stats.textures() + " textures  •  " + formatBytes(stats.totalBytes())), MUTED));
            for (AvatarValidationReport.Issue issue : report.issues()) {
                int issueColor = issue.severity() == AvatarValidationReport.Severity.ERROR ? ERROR : WARNING;
                String file = issue.file() == null || issue.file().isBlank() ? "" : " (" + issue.file() + ")";
                result.add(new Line(Component.literal("  " + (issue.severity() == AvatarValidationReport.Severity.ERROR ? "× " : "! ") + issue.message() + file), issueColor));
            }
            result.add(new Line(Component.literal(""), MUTED));
        }
        return List.copyOf(result);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA8030710);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BACKGROUND);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, ACCENT);
        graphics.text(font, title, panelX + 14, panelY + 13, 0xFFF4F7FC, true);
        graphics.text(font, Component.translatable("screen.shyne_core.validation.subtitle"), panelX + 14, panelY + 29, MUTED, false);
        int start = page * pageSize;
        int end = Math.min(lines.size(), start + pageSize);
        int y = panelY + 51;
        for (int i = start; i < end; i++) {
            Line line = lines.get(i);
            String clipped = font.plainSubstrByWidth(line.text().getString(), panelWidth - 28);
            graphics.text(font, Component.literal(clipped), panelX + 14, y, line.color(), false);
            y += 12;
        }
        graphics.text(font, Component.translatable("screen.shyne_core.validation.page", page + 1, pages), panelX + 88, panelY + panelHeight - 24, MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void openFolder() {
        try {
            Files.createDirectories(AvatarLoader.avatarsDir());
            Util.getPlatform().openPath(AvatarLoader.avatarsDir());
        } catch (IOException error) {
            ShyneCore.LOGGER.error("[AvatarValidation] Could not open Avatar folder: {}", error.getMessage());
        }
    }
    private void openPage(int value) { if (minecraft != null) minecraft.gui.setScreen(new AvatarValidationScreen(parent, value, false)); }
    private void rescan() { if (minecraft != null) minecraft.gui.setScreen(new AvatarValidationScreen(parent, 0, true)); }
    @Override public void onClose() { if (minecraft != null) minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
    private record Line(Component text, int color) {}
}
