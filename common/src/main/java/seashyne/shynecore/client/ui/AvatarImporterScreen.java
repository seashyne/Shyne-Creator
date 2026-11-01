package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarCatalogEntry;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.importer.AvatarDragDropInstaller;
import seashyne.shynecore.client.avatar.importer.AvatarImportReport;
import seashyne.shynecore.client.avatar.importer.AvatarImporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game Studio UI for inspecting, diagnosing, and converting Figura and Blockbench
 * projects into Shyne Avatar Standard 2.0 with preserved Creator Attribution.
 */
public final class AvatarImporterScreen extends Screen {
    private static final int ITEM_HEIGHT = 22;

    private final Screen parent;
    private List<Candidate> candidates = new ArrayList<>();
    private int selectedIndex = 0;
    private int candidateScroll = 0;
    private AvatarImportReport currentReport;
    private String statusMessage = "";
    private int statusColor = ShyneTheme.SUCCESS;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private int leftWidth;
    private int rightX;
    private int rightWidth;

    private ShyneButton convertButton;
    private ShyneButton openFolderButton;
    private ShyneButton exportButton;

    public AvatarImporterScreen(Screen parent) {
        super(Component.literal("Avatar Importer & Creator Tooling"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(840, Math.max(340, width - 20));
        panelHeight = Math.min(460, Math.max(260, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        leftWidth = Math.min(260, panelWidth / 3);
        rightX = panelX + leftWidth + 14;
        rightWidth = panelWidth - leftWidth - 28;

        loadCandidates();

        int footerY = panelY + panelHeight - 32;

        convertButton = addRenderableWidget(ShyneButton.primary(Component.literal("Convert to Shyne 2.0"), btn -> executeConvert())
            .bounds(rightX, footerY, 140, 20));

        exportButton = addRenderableWidget(ShyneButton.secondary(Component.literal("Export Report"), btn -> executeExportReport())
            .bounds(rightX + 145, footerY, 95, 20));

        openFolderButton = addRenderableWidget(ShyneButton.secondary(Component.literal("Open Folder"), btn -> executeOpenFolder())
            .bounds(rightX + 245, footerY, 85, 20));

        addRenderableWidget(ShyneButton.secondary(Component.literal("Refresh"), btn -> {
            loadCandidates();
            statusMessage = "Refreshed candidates list.";
            statusColor = ShyneTheme.TEXT_SECONDARY;
        }).bounds(panelX + 14, footerY, 70, 20));

        addRenderableWidget(ShyneButton.secondary(Component.translatable("gui.back"), btn -> onClose())
            .bounds(panelX + panelWidth - 75, footerY, 65, 20));

        selectCandidate(selectedIndex);
    }

    private void loadCandidates() {
        candidates.clear();
        Path avatarsDir = AvatarLoader.avatarsDir();

        // 1. Unimported candidates
        List<Path> unimported = AvatarImporter.scanUnimported(avatarsDir);
        for (Path p : unimported) {
            candidates.add(new Candidate(p.getFileName().toString(), p, true));
        }

        // 2. Existing avatars in catalog
        for (AvatarCatalogEntry entry : AvatarRuntime.catalog()) {
            candidates.add(new Candidate(entry.name() + " (" + entry.id() + ")", entry.root(), false));
        }

        if (candidates.isEmpty()) {
            candidates.add(new Candidate("No avatar folders found", avatarsDir, false));
        }

        if (selectedIndex >= candidates.size()) {
            selectedIndex = 0;
        }
        int maxVisible = Math.max(1, (panelHeight - 40 - 66) / ITEM_HEIGHT);
        int maxCandidateScroll = Math.max(0, candidates.size() - maxVisible);
        candidateScroll = Math.max(0, Math.min(candidateScroll, maxCandidateScroll));
    }

    private void selectCandidate(int index) {
        if (index < 0 || index >= candidates.size()) return;
        selectedIndex = index;
        Candidate candidate = candidates.get(index);
        if (Files.isDirectory(candidate.path())) {
            currentReport = AvatarImporter.analyze(candidate.path());
            boolean canConvert = Files.isDirectory(candidate.path());
            if (convertButton != null) convertButton.active = canConvert;
            if (exportButton != null) exportButton.active = currentReport != null;
            if (openFolderButton != null) openFolderButton.active = Files.isDirectory(candidate.path());
        } else {
            currentReport = null;
            if (convertButton != null) convertButton.active = false;
            if (exportButton != null) exportButton.active = false;
            if (openFolderButton != null) openFolderButton.active = false;
        }
    }

    private void executeConvert() {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return;
        Candidate c = candidates.get(selectedIndex);
        if (!Files.isDirectory(c.path())) return;

        AvatarImporter.ConversionResult result = AvatarImporter.convert(c.path());
        if (result.success()) {
            statusMessage = "Successfully imported '" + result.report().avatarName() + "' with Creator Attribution!";
            statusColor = ShyneTheme.SUCCESS;
            AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, err) -> {
                if (minecraft != null) minecraft.execute(() -> {
                    loadCandidates();
                    selectCandidate(selectedIndex);
                });
            });
        } else {
            statusMessage = "Import failed: " + result.message();
            statusColor = ShyneTheme.DANGER;
        }
    }

    private void executeExportReport() {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return;
        Candidate c = candidates.get(selectedIndex);
        if (!Files.isDirectory(c.path())) return;

        try {
            Path exported = AvatarImporter.exportReport(c.path());
            statusMessage = "Saved report to " + exported.getFileName();
            statusColor = ShyneTheme.SUCCESS;
        } catch (IOException e) {
            statusMessage = "Failed to export report: " + e.getMessage();
            statusColor = ShyneTheme.DANGER;
        }
    }

    private void executeOpenFolder() {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return;
        Candidate c = candidates.get(selectedIndex);
        try {
            Files.createDirectories(c.path());
            Blaze3D.openPath(c.path());
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[AvatarImporter] Could not open folder: {}", e.getMessage());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int maxVisible = Math.max(1, (panelHeight - 40 - 66) / ITEM_HEIGHT);
            int listY = panelY + 66;
            for (int i = 0; i < maxVisible; i++) {
                int idx = candidateScroll + i;
                if (idx >= candidates.size()) break;
                int itemY = listY + i * ITEM_HEIGHT;
                if (mouseX >= panelX + 12 && mouseX <= panelX + 10 + leftWidth && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                    selectCandidate(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxVisible = Math.max(1, (panelHeight - 40 - 66) / ITEM_HEIGHT);
        int maxCandidateScroll = Math.max(0, candidates.size() - maxVisible);
        if (verticalAmount != 0 && maxCandidateScroll > 0) {
            candidateScroll = Math.max(0, Math.min(maxCandidateScroll, candidateScroll + (verticalAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ShyneTheme.drawBackdrop(graphics, width, height);
        ShyneTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 46);

        // Header
        graphics.text(font, Component.literal("Shyne Avatar Importer & Tooling").withStyle(ChatFormatting.BOLD), panelX + 16, panelY + 14, ShyneTheme.TEXT_PRIMARY, true);
        graphics.text(font, Component.literal("Inspect, convert, and preserve creator rights for Figura & Blockbench avatars"), panelX + 16, panelY + 31, ShyneTheme.TEXT_SECONDARY, false);

        // Left list panel inset
        ShyneTheme.drawInset(graphics, panelX + 10, panelY + 46, leftWidth, panelHeight - 84, ShyneTheme.BORDER_SUBTLE);
        String scrollHint = candidates.size() > Math.max(1, (panelHeight - 40 - 66) / ITEM_HEIGHT) ? " (Scroll ↕)" : "";
        graphics.text(font, Component.literal("Detected Folders (" + candidates.size() + ")" + scrollHint), panelX + 16, panelY + 52, ShyneTheme.ACCENT_BRIGHT, false);

        int maxVisible = Math.max(1, (panelHeight - 40 - 66) / ITEM_HEIGHT);
        int listY = panelY + 66;
        for (int i = 0; i < maxVisible; i++) {
            int idx = candidateScroll + i;
            if (idx >= candidates.size()) break;
            Candidate c = candidates.get(idx);
            int itemY = listY + i * ITEM_HEIGHT;
            boolean selected = (idx == selectedIndex);
            boolean hovered = mouseX >= panelX + 12 && mouseX <= panelX + 10 + leftWidth && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT - 2;

            ShyneTheme.drawCard(graphics, panelX + 12, itemY, leftWidth - 6, ITEM_HEIGHT - 2, hovered, selected);

            int color = c.isUnimported() ? ShyneTheme.WARNING : ShyneTheme.TEXT_PRIMARY;
            String prefix = c.isUnimported() ? "! " : "✓ ";
            String clipped = font.plainSubstrByWidth(prefix + c.displayName(), leftWidth - 20);
            graphics.text(font, Component.literal(clipped), panelX + 16, itemY + 3, color, false);
        }

        if (candidates.size() > maxVisible) {
            int trackX = panelX + 10 + leftWidth - 3;
            int trackTop = panelY + 66;
            int trackBottom = panelY + panelHeight - 44;
            int maxScroll = candidates.size() - maxVisible;
            ShyneTheme.drawScrollbar(graphics, trackX, trackTop, trackBottom, candidateScroll, maxScroll, maxVisible, candidates.size());
        }

        // Right details panel inset
        ShyneTheme.drawInset(graphics, rightX - 4, panelY + 46, rightWidth + 4, panelHeight - 84, ShyneTheme.BORDER_SUBTLE);

        if (currentReport != null) {
            int ry = panelY + 54;
            AvatarImportReport r = currentReport;

            // Title & Source Type
            graphics.text(font, Component.literal(r.avatarName()).withStyle(ChatFormatting.BOLD), rightX + 6, ry, ShyneTheme.TEXT_PRIMARY, true);
            graphics.text(font, Component.literal("ID: " + r.avatarId() + "  |  Source: " + r.sourceType()), rightX + 6, ry + 14, ShyneTheme.TEXT_SECONDARY, false);

            // Grade Badge
            AvatarImportReport.CompatibilityGrade grade = r.grade();
            int gradeFill = switch (grade) {
                case A_PERFECT -> ShyneTheme.SUCCESS_SOFT;
                case B_COMPATIBLE_WITH_WARNINGS -> ShyneTheme.WARNING_SOFT;
                case C_NEEDS_MODIFICATIONS -> 0x30FFB050;
                case D_INCOMPATIBLE -> ShyneTheme.DANGER_SOFT;
            };
            int gradeCol = switch (grade) {
                case A_PERFECT -> ShyneTheme.SUCCESS;
                case B_COMPATIBLE_WITH_WARNINGS -> ShyneTheme.WARNING;
                case C_NEEDS_MODIFICATIONS -> 0xFFFFB050;
                case D_INCOMPATIBLE -> ShyneTheme.DANGER;
            };
            ShyneTheme.drawBadge(graphics, font, grade.label(), rightX + rightWidth - 140, ry, gradeFill, gradeCol, gradeCol);

            // Metrics Box
            ry += 32;
            ShyneTheme.drawCard(graphics, rightX + 4, ry, rightWidth - 8, 44, false, false);
            var m = r.modelStats();
            graphics.text(font, Component.literal("Model: " + m.cubeCount() + " cubes  •  " + m.meshCount() + " meshes  •  " + m.boneCount() + " bones  •  " + m.textureCount() + " textures (" + m.maxTextureWidth() + "x" + m.maxTextureHeight() + ")"), rightX + 10, ry + 6, ShyneTheme.TEXT_PRIMARY, false);
            var s = r.scriptStats();
            graphics.text(font, Component.literal("Scripts: " + s.scriptCount() + " files (" + s.totalLines() + " lines)  •  APIs: " + (s.detectedApis().isEmpty() ? "none" : String.join(", ", s.detectedApis()))), rightX + 10, ry + 18, ShyneTheme.TEXT_PRIMARY, false);
            graphics.text(font, Component.literal("Animations: " + m.animationCount() + "  •  Keyframes: " + m.totalKeyframes()), rightX + 10, ry + 30, ShyneTheme.TEXT_SECONDARY, false);

            // Creator Attribution Box
            ry += 52;
            ShyneTheme.drawCard(graphics, rightX + 4, ry, rightWidth - 8, 48, false, true);
            graphics.text(font, Component.literal("Creator Attribution & Licensing").withStyle(ChatFormatting.BOLD), rightX + 10, ry + 6, ShyneTheme.ACCENT, false);
            String author = r.author().isBlank() ? "Original Creator" : r.author();
            graphics.text(font, Component.literal("Author: " + author + "  •  Format: " + r.modelFormat()), rightX + 10, ry + 18, ShyneTheme.TEXT_PRIMARY, false);
            graphics.text(font, Component.literal("Notice: Preserves original creator copyright in ATTRIBUTION.md and avatar.json"), rightX + 10, ry + 32, ShyneTheme.TEXT_SECONDARY, false);

            // Diagnostic Warnings / Issues
            ry += 56;
            graphics.text(font, Component.literal("Diagnostic Findings (" + r.issues().size() + "):"), rightX + 6, ry, ShyneTheme.TEXT_PRIMARY, false);
            ry += 14;
            if (r.issues().isEmpty()) {
                graphics.text(font, Component.literal("✨ No issues detected. Clean avatar configuration!"), rightX + 6, ry, ShyneTheme.SUCCESS, false);
            } else {
                for (int i = 0; i < r.issues().size() && i < 3; i++) {
                    var issue = r.issues().get(i);
                    int col = issue.severity() == seashyne.shynecore.client.avatar.linter.AvatarLintIssue.Severity.ERROR ? ShyneTheme.DANGER : ShyneTheme.WARNING;
                    String icon = issue.severity() == seashyne.shynecore.client.avatar.linter.AvatarLintIssue.Severity.ERROR ? "× " : "! ";
                    String clipped = font.plainSubstrByWidth(icon + issue.message(), rightWidth - 20);
                    graphics.text(font, Component.literal(clipped), rightX + 6, ry + i * 12, col, false);
                }
            }
        }

        // Status Message
        if (!statusMessage.isEmpty()) {
            graphics.text(font, Component.literal(statusMessage), rightX + 6, panelY + panelHeight - 48, statusColor, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        AvatarDragDropInstaller.DropResult result = AvatarDragDropInstaller.installDroppedFiles(files);
        statusMessage = result.message();
        statusColor = result.installedCount() > 0 ? ShyneTheme.SUCCESS : ShyneTheme.WARNING;
        if (result.installedCount() > 0) {
            AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, err) -> {
                if (minecraft != null) {
                    minecraft.execute(() -> {
                        loadCandidates();
                        selectCandidate(0);
                    });
                }
            });
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record Candidate(String displayName, Path path, boolean isUnimported) {}
}
