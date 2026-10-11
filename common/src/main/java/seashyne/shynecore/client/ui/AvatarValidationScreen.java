package seashyne.shynecore.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;
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
    private int requestedPage;
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
        int btnWidth = Math.min(84, Math.max(64, (panelWidth - 140) / 4));
        int right = panelX + panelWidth - 14;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(right - btnWidth, footerY, btnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.validation.folder"), button -> openFolder())
            .bounds(right - btnWidth * 2 - 4, footerY, btnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.shyne_core.validation.rescan"), button -> rescan())
            .bounds(right - btnWidth * 3 - 8, footerY, btnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Importer"), button -> {
            if (minecraft != null) minecraft.gui.setScreen(new AvatarImporterScreen(this));
        }).bounds(right - btnWidth * 4 - 12, footerY, btnWidth, 20).build());

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
        Component scrollHint = pages > 1
            ? Component.literal("Scroll ↕  " + (page + 1) + " / " + pages)
            : Component.translatable("screen.shyne_core.validation.page", page + 1, pages);
        graphics.text(font, scrollHint, panelX + 14, panelY + panelHeight - 24, MUTED, false);

        if (pages > 1) {
            int trackX = panelX + panelWidth - 8;
            int trackTop = panelY + 50;
            int trackBottom = panelY + panelHeight - 36;
            int trackHeight = trackBottom - trackTop;
            if (trackHeight > 10) {
                graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0x33445A78);
                int thumbHeight = Math.max(8, trackHeight / pages);
                int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * ((float) page / (pages - 1)));
                graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void openFolder() {
        try {
            Files.createDirectories(AvatarLoader.avatarsDir());
            Blaze3D.openPath(AvatarLoader.avatarsDir());
        } catch (IOException error) {
            ShyneCore.LOGGER.error("[AvatarValidation] Could not open Avatar folder: {}", error.getMessage());
        }
    }
    private void openPage(int value) {
        this.requestedPage = Math.max(0, Math.min(value, pages - 1));
        this.page = this.requestedPage;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && pages > 1) {
            int newPage = Math.max(0, Math.min(pages - 1, page + (verticalAmount < 0 ? 1 : -1)));
            if (newPage != page) {
                openPage(newPage);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    private void rescan() { if (minecraft != null) minecraft.gui.setScreen(new AvatarValidationScreen(parent, 0, true)); }
    @Override public void onClose() { if (minecraft != null) minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
    private record Line(Component text, int color) {}
}
