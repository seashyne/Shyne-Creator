package seashyne.shynecore.client.ui;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
 * In-game UI for inspecting, diagnosing, and converting Figura and Blockbench
 * projects into Shyne Avatar Standard 2.0 with preserved Creator Attribution.
 */
public final class AvatarImporterScreen extends Screen {
    private static final int BACKGROUND = 0xF20B1222;
    private static final int CARD_BG = 0x88121E36;
    private static final int ACCENT = 0xFF3DD9E8;
    private static final int OK = 0xFF7DDBB3;
    private static final int WARNING = 0xFFFFC66D;
    private static final int ERROR = 0xFFFF8793;
    private static final int MUTED = 0xFF91A0B7;

    private final Screen parent;
    private List<Candidate> candidates = new ArrayList<>();
    private int selectedIndex = 0;
    private int candidateScroll = 0;
    private AvatarImportReport currentReport;
    private String statusMessage = "";
    private int statusColor = OK;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private int leftWidth;
    private int rightX;
    private int rightWidth;

    private Button convertButton;
    private Button openFolderButton;
    private Button exportButton;

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

        convertButton = addRenderableWidget(Button.builder(Component.literal("Convert to Shyne 2.0"), btn -> executeConvert())
            .bounds(rightX, footerY, 140, 20).build());

        exportButton = addRenderableWidget(Button.builder(Component.literal("Export Report"), btn -> executeExportReport())
            .bounds(rightX + 145, footerY, 95, 20).build());

        openFolderButton = addRenderableWidget(Button.builder(Component.literal("Open Folder"), btn -> executeOpenFolder())
            .bounds(rightX + 245, footerY, 85, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> {
            loadCandidates();
            statusMessage = "Refreshed candidates list.";
            statusColor = MUTED;
        }).bounds(panelX + 14, footerY, 70, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), btn -> onClose())
            .bounds(panelX + panelWidth - 75, footerY, 65, 20).build());

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
        int maxVisible = Math.max(1, (panelHeight - 40 - 66) / 20);
        int maxCandidateScroll = Math.max(0, candidates.size() - maxVisible);
        candidateScroll = Math.max(0, Math.min(candidateScroll, maxCandidateScroll));
    }

    private void selectCandidate(int index) {
        if (index < 0 || index >= candidates.size()) return;
        selectedIndex = index;
        Candidate candidate = candidates.get(index);
        if (Files.isDirectory(candidate.path())) {
            currentReport = AvatarImporter.analyze(candidate.path());
            boolean canConvert = candidate.isUnimported() || currentReport.sourceType() != AvatarImportReport.SourceType.SHYNE_AVATAR || currentReport.warningCount() > 0;
            if (convertButton != null) convertButton.active = Files.isDirectory(candidate.path());
            if (exportButton != null) exportButton.active = currentReport != null;
            if (openFolderButton != null) openFolderButton.active = Files.isDirectory(candidate.path());
        } else {
            currentReport = null;
            if (convertButton != null) convertButton.active = false;
            if (exportButton != null) exportButton.active = false;
        }
    }

    private void executeConvert() {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return;
        Candidate c = candidates.get(selectedIndex);
        if (!Files.isDirectory(c.path())) return;

        AvatarImporter.ConversionResult result = AvatarImporter.convert(c.path());
        if (result.success()) {
            statusMessage = "Successfully imported '" + result.report().avatarName() + "' with Creator Attribution!";
            statusColor = OK;
            AvatarRuntime.refreshCatalogAsync(true).whenComplete((entries, err) -> {
                if (minecraft != null) minecraft.execute(() -> {
                    loadCandidates();
                    selectCandidate(selectedIndex);
                });
            });
        } else {
            statusMessage = "Import failed: " + result.message();
            statusColor = ERROR;
        }
    }

    private void executeExportReport() {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return;
        Candidate c = candidates.get(selectedIndex);
        if (!Files.isDirectory(c.path())) return;

        try {
            Path exported = AvatarImporter.exportReport(c.path());
            statusMessage = "Saved report to " + exported.getFileName();
            statusColor = OK;
        } catch (IOException e) {
            statusMessage = "Failed to export report: " + e.getMessage();
            statusColor = ERROR;
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
            int maxVisible = Math.max(1, (panelHeight - 40 - 66) / 20);
            int listY = panelY + 66;
            for (int i = 0; i < maxVisible; i++) {
                int idx = candidateScroll + i;
                if (idx >= candidates.size()) break;
                int itemY = listY + i * 20;
                if (mouseX >= panelX + 12 && mouseX <= panelX + 10 + leftWidth && mouseY >= itemY && mouseY < itemY + 20) {
                    selectCandidate(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxVisible = Math.max(1, (panelHeight - 40 - 66) / 20);
        int maxCandidateScroll = Math.max(0, candidates.size() - maxVisible);
        if (verticalAmount != 0 && maxCandidateScroll > 0) {
            candidateScroll = Math.max(0, Math.min(maxCandidateScroll, candidateScroll + (verticalAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA8030710);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BACKGROUND);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, ACCENT);

        // Header
        graphics.text(font, Component.literal("Shyne Avatar Importer & Tooling").withStyle(ChatFormatting.BOLD), panelX + 14, panelY + 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Inspect, convert, and preserve creator rights for Figura & Blockbench avatars"), panelX + 14, panelY + 26, MUTED, false);

        // Left list panel background
        graphics.fill(panelX + 10, panelY + 44, panelX + 10 + leftWidth, panelY + panelHeight - 40, CARD_BG);
        String scrollHint = candidates.size() > Math.max(1, (panelHeight - 40 - 66) / 20) ? " (Scroll ↕)" : "";
        graphics.text(font, Component.literal("Detected Folders (" + candidates.size() + ")" + scrollHint).withStyle(ChatFormatting.UNDERLINE), panelX + 16, panelY + 50, 0xFFFFFFFF, false);

        int maxVisible = Math.max(1, (panelHeight - 40 - 66) / 20);
        int listY = panelY + 66;
        for (int i = 0; i < maxVisible; i++) {
            int idx = candidateScroll + i;
            if (idx >= candidates.size()) break;
            Candidate c = candidates.get(idx);
            int itemY = listY + i * 20;
            boolean selected = (idx == selectedIndex);
            if (selected) {
                graphics.fill(panelX + 12, itemY - 2, panelX + 10 + leftWidth - 2, itemY + 18, 0x443DD9E8);
            }
            int color = c.isUnimported() ? WARNING : 0xFFE2E8F0;
            String prefix = c.isUnimported() ? "[!] " : "[✓] ";
            String clipped = font.plainSubstrByWidth(prefix + c.displayName(), leftWidth - 16);
            graphics.text(font, Component.literal(clipped), panelX + 16, itemY + 2, color, false);
        }

        if (candidates.size() > maxVisible) {
            int trackX = panelX + 10 + leftWidth - 3;
            int trackTop = panelY + 66;
            int trackBottom = panelY + panelHeight - 44;
            int trackHeight = trackBottom - trackTop;
            int maxScroll = candidates.size() - maxVisible;
            if (trackHeight > 10 && maxScroll > 0) {
                graphics.fill(trackX, trackTop, trackX + 2, trackBottom, 0x33445A78);
                int thumbHeight = Math.max(8, trackHeight * maxVisible / candidates.size());
                int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * ((float) candidateScroll / maxScroll));
                graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT);
            }
        }

        // Right details panel
        graphics.fill(rightX - 4, panelY + 44, panelX + panelWidth - 10, panelY + panelHeight - 40, CARD_BG);

        if (currentReport != null) {
            int ry = panelY + 52;
            AvatarImportReport r = currentReport;

            // Title & Source Type
            graphics.text(font, Component.literal(r.avatarName()).withStyle(ChatFormatting.BOLD), rightX, ry, 0xFFFFFFFF, true);
            graphics.text(font, Component.literal("ID: " + r.avatarId() + "  |  Source: " + r.sourceType()), rightX, ry + 14, MUTED, false);

            // Grade Badge
            AvatarImportReport.CompatibilityGrade grade = r.grade();
            int gradeCol = switch (grade) {
                case A_PERFECT -> OK;
                case B_COMPATIBLE_WITH_WARNINGS -> WARNING;
                case C_NEEDS_MODIFICATIONS -> 0xFFFFB050;
                case D_INCOMPATIBLE -> ERROR;
            };
            graphics.text(font, Component.literal("Compatibility: " + grade.label()), rightX + rightWidth - 190, ry, gradeCol, true);

            // Metrics Box
            ry += 32;
            graphics.fill(rightX, ry, rightX + rightWidth - 10, ry + 44, 0x33000000);
            var m = r.modelStats();
            graphics.text(font, Component.literal("Model: " + m.cubeCount() + " cubes  •  " + m.meshCount() + " meshes  •  " + m.boneCount() + " bones  •  " + m.textureCount() + " textures (" + m.maxTextureWidth() + "x" + m.maxTextureHeight() + ")"), rightX + 6, ry + 6, 0xFFE2E8F0, false);
            var s = r.scriptStats();
            graphics.text(font, Component.literal("Scripts: " + s.scriptCount() + " files (" + s.totalLines() + " lines)  •  APIs: " + (s.detectedApis().isEmpty() ? "none" : String.join(", ", s.detectedApis()))), rightX + 6, ry + 18, 0xFFE2E8F0, false);
            graphics.text(font, Component.literal("Animations: " + m.animationCount() + "  •  Keyframes: " + m.totalKeyframes()), rightX + 6, ry + 30, MUTED, false);

            // Creator Attribution Box
            ry += 52;
            graphics.fill(rightX, ry, rightX + rightWidth - 10, ry + 48, 0x33203450);
            graphics.text(font, Component.literal("Creator Attribution & Licensing").withStyle(ChatFormatting.BOLD), rightX + 6, ry + 6, 0xFF3DD9E8, false);
            String author = r.author().isBlank() ? "Original Creator" : r.author();
            graphics.text(font, Component.literal("Author: " + author + "  •  Format: " + r.modelFormat()), rightX + 6, ry + 18, 0xFFFFFFFF, false);
            graphics.text(font, Component.literal("Notice: Preserves original creator copyright in ATTRIBUTION.md and avatar.json"), rightX + 6, ry + 32, MUTED, false);

            // Diagnostic Warnings / Issues
            ry += 56;
            graphics.text(font, Component.literal("Diagnostic Findings (" + r.issues().size() + "):").withStyle(ChatFormatting.UNDERLINE), rightX, ry, 0xFFFFFFFF, false);
            ry += 14;
            if (r.issues().isEmpty()) {
                graphics.text(font, Component.literal("✨ No issues detected. Clean avatar configuration!"), rightX, ry, OK, false);
            } else {
                for (int i = 0; i < r.issues().size() && i < 3; i++) {
                    var issue = r.issues().get(i);
                    int col = issue.severity() == seashyne.shynecore.client.avatar.linter.AvatarLintIssue.Severity.ERROR ? ERROR : WARNING;
                    String icon = issue.severity() == seashyne.shynecore.client.avatar.linter.AvatarLintIssue.Severity.ERROR ? "× " : "! ";
                    String clipped = font.plainSubstrByWidth(icon + issue.message(), rightWidth - 20);
                    graphics.text(font, Component.literal(clipped), rightX, ry + i * 12, col, false);
                }
            }
        }

        // Status Message
        if (!statusMessage.isEmpty()) {
            graphics.text(font, Component.literal(statusMessage), rightX, panelY + panelHeight - 48, statusColor, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        AvatarDragDropInstaller.DropResult result = AvatarDragDropInstaller.installDroppedFiles(files);
        statusMessage = result.message();
        statusColor = result.installedCount() > 0 ? OK : WARNING;
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
