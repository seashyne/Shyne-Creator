package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarValidationReport;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic validation screen for local & imported avatars, listing
 * geometry budgets, performance metrics, and syntax / runtime linting issues.
 */
public final class AvatarValidationScreen extends Screen {
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

    public AvatarValidationScreen(Screen parent) {
        this(parent, 0, true);
    }

    private AvatarValidationScreen(Screen parent, int page, boolean refreshOnOpen) {
        super(Component.translatable("screen.shyne_core.validation.title"));
        this.parent = parent;
        this.requestedPage = Math.max(0, page);
        this.refreshOnOpen = refreshOnOpen;
    }

    @Override
    protected void init() {
        lines = buildLines(AvatarRuntime.catalog());
        panelWidth = Math.min(780, Math.max(300, width - 20));
        panelHeight = Math.min(440, Math.max(220, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        pageSize = Math.max(6, (panelHeight - 100) / 13);
        pages = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        page = Math.min(requestedPage, pages - 1);

        int footerY = panelY + panelHeight - 30;
        int btnWidth = Math.min(96, Math.max(70, (panelWidth - 140) / 4));
        int right = panelX + panelWidth - 14;

        addRenderableWidget(ShyneButton.primary(Component.translatable("gui.back"), button -> onClose())
            .bounds(right - btnWidth, footerY, btnWidth, 20));
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.validation.folder"), button -> openFolder())
            .bounds(right - btnWidth * 2 - 6, footerY, btnWidth, 20));
        addRenderableWidget(ShyneButton.secondary(Component.translatable("screen.shyne_core.validation.rescan"), button -> rescan())
            .bounds(right - btnWidth * 3 - 12, footerY, btnWidth, 20));
        addRenderableWidget(ShyneButton.secondary(Component.literal("Importer"), button -> {
            if (minecraft != null) minecraft.gui.setScreen(new AvatarImporterScreen(this));
        }).bounds(right - btnWidth * 4 - 18, footerY, btnWidth, 20));

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
            result.add(new Line(Component.translatable("screen.shyne_core.validation.empty"), ShyneTheme.TEXT_SECONDARY));
            return List.copyOf(result);
        }
        for (AvatarCatalogEntry entry : catalog) {
            AvatarValidationReport report = entry.validation();
            int color = entry.valid() ? report.warningCount() > 0 ? ShyneTheme.WARNING : ShyneTheme.SUCCESS : ShyneTheme.DANGER;
            String status = entry.valid() ? report.warningCount() > 0 ? "[!] WARNING" : "[✓] OK" : "[✗] ERROR";
            result.add(new Line(Component.literal(status + "  " + entry.name() + "  [" + entry.id() + "]"), color));
            var stats = report.stats();
            result.add(new Line(Component.literal("    " + stats.bones() + " bones  •  " + stats.cubes() + " cubes  •  "
                + stats.animations() + " animations  •  " + stats.textures() + " textures  •  " + formatBytes(stats.totalBytes())), ShyneTheme.TEXT_SECONDARY));
            for (AvatarValidationReport.Issue issue : report.issues()) {
                int issueColor = issue.severity() == AvatarValidationReport.Severity.ERROR ? ShyneTheme.DANGER : ShyneTheme.WARNING;
                String file = issue.file() == null || issue.file().isBlank() ? "" : " (" + issue.file() + ")";
                result.add(new Line(Component.literal("    " + (issue.severity() == AvatarValidationReport.Severity.ERROR ? "× " : "! ") + issue.message() + file), issueColor));
            }
            result.add(new Line(Component.literal(""), ShyneTheme.TEXT_DIM));
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
        ShyneTheme.drawBackdrop(graphics, width, height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 46);

        graphics.text(font, title, panelX + 16, panelY + 14, ShyneTheme.TEXT_PRIMARY, true);
        graphics.text(font, Component.translatable("screen.shyne_core.validation.subtitle"), panelX + 16, panelY + 31, ShyneTheme.TEXT_SECONDARY, false);

        int start = page * pageSize;
        int end = Math.min(lines.size(), start + pageSize);
        int y = panelY + 54;
        for (int i = start; i < end; i++) {
            Line line = lines.get(i);
            String text = line.text().getString();
            if (!text.isEmpty()) {
                String clipped = font.plainSubstrByWidth(text, panelWidth - 32);
                graphics.text(font, Component.literal(clipped), panelX + 16, y, line.color(), false);
            }
            y += 13;
        }

        Component scrollHint = pages > 1
            ? Component.literal("Scroll ↕  " + (page + 1) + " / " + pages)
            : Component.translatable("screen.shyne_core.validation.page", page + 1, pages);
        graphics.text(font, scrollHint, panelX + 16, panelY + panelHeight - 24, ShyneTheme.TEXT_SECONDARY, false);

        if (pages > 1) {
            int trackX = panelX + panelWidth - 10;
            int trackTop = panelY + 54;
            int trackBottom = panelY + panelHeight - 36;
            ShyneTheme.drawScrollbar(graphics, trackX, trackTop, trackBottom, page, pages - 1, pageSize, lines.size());
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

    private void rescan() {
        if (minecraft != null) minecraft.gui.setScreen(new AvatarValidationScreen(parent, 0, true));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record Line(Component text, int color) {}
}
