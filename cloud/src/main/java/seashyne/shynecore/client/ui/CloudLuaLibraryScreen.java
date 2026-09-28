package seashyne.shynecore.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.client.avatar.ShyneLibraryManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UI for browsing and downloading Community Lua Libraries from Seashyne Libraries Hub.
 * Part of the Hybrid Library Architecture:
 * 1. Local avatar scripts
 * 2. Built-in Mod JAR classpath libraries
 * 3. Local cached libraries (.minecraft/shyne_creator/libs/)
 */
public final class CloudLuaLibraryScreen extends Screen {
    public record LibraryItem(String id, String name, String version, String category, String description, String rawUrl, String cdnUrl) {}

    private static final String REGISTRY_URL = "https://raw.githubusercontent.com/seashyne/Libraries/main/registry.json";
    private static final int ROW_HEIGHT = 44;
    private static final int MAX_VISIBLE_ROWS = 6;

    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight, footerY, pagerY;
    private EditBox searchBox;
    private String searchText = "";
    private List<LibraryItem> allItems = new ArrayList<>();
    private List<LibraryItem> visibleItems = new ArrayList<>();
    private final Map<String, String> statusOverrides = new ConcurrentHashMap<>();
    private boolean loading = false;
    private int offset = 0;

    public CloudLuaLibraryScreen(Screen parent) {
        super(Component.translatable("screen.shyne_core.cloud.lua_libs.title"));
        this.parent = parent;
        loadDefaultFallback();
    }

    private void loadDefaultFallback() {
        allItems.clear();
        allItems.add(new LibraryItem("lua-classic", "classic", "0.1.0", "OOP", "Tiny class-based OOP for Lua", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/classic.lua", ""));
        allItems.add(new LibraryItem("lua-tween", "tween", "2.1.1", "Animation", "Tweening & Easing equations", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/tween.lua", ""));
        allItems.add(new LibraryItem("lua-noise", "noise", "1.0.0", "Math", "1D/2D/3D Perlin & Simplex noise", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/noise.lua", ""));
        allItems.add(new LibraryItem("lua-vector", "vector", "1.0.0", "Math", "2D/3D Vector math & lerp", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/vector.lua", ""));
        allItems.add(new LibraryItem("lua-signal", "signal", "1.0.0", "Events", "Event/Signal observer dispatcher", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/signal.lua", ""));
        allItems.add(new LibraryItem("lua-inspect", "inspect", "3.1.0", "Debug", "Table serializer & debug printer", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/inspect.lua", ""));
        allItems.add(new LibraryItem("lua-matrix", "matrix", "1.0.0", "Math", "4x4 Transform & Projection matrix", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/matrix.lua", ""));
        allItems.add(new LibraryItem("lua-state", "state", "1.0.0", "FSM", "Finite State Machine controller", "https://raw.githubusercontent.com/seashyne/Libraries/main/lua/state.lua", ""));
        filterItems();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, Math.max(1, this.width - 20));
        panelHeight = Math.min(390, Math.max(1, this.height - 16));
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(8, (this.height - panelHeight) / 2);
        footerY = panelY + panelHeight - 32;
        pagerY = footerY - 22;

        searchBox = new EditBox(this.font, panelX + 14, panelY + 43, Math.max(120, panelWidth - 270), 20, Component.translatable("screen.shyne_core.cloud.search"));
        searchBox.setHint(Component.translatable("screen.shyne_core.cloud.search"));
        searchBox.setValue(searchText);
        addRenderableWidget(searchBox);

        Button openFolderBtn = Button.builder(Component.translatable("screen.shyne_core.cloud.lua_libs.open_folder"), b -> openFolder())
            .bounds(panelX + panelWidth - 250, panelY + 43, 116, 20).build();
        addRenderableWidget(openFolderBtn);

        Button refreshBtn = Button.builder(Component.translatable("screen.shyne_core.cloud.lua_libs.refresh"), b -> fetchRegistry())
            .bounds(panelX + panelWidth - 128, panelY + 43, 114, 20).build();
        addRenderableWidget(refreshBtn);

        int listX = panelX + 14;
        int listY = panelY + 74;
        int listWidth = panelWidth - 28;
        int maxRows = Math.min(MAX_VISIBLE_ROWS, visibleItems.size() - offset);

        for (int i = 0; i < maxRows; i++) {
            final int index = offset + i;
            if (index >= visibleItems.size()) break;
            LibraryItem item = visibleItems.get(index);
            int rowY = listY + i * ROW_HEIGHT;
            int btnWidth = 96;
            int btnX = listX + listWidth - btnWidth - 6;

            boolean builtIn = ShyneLibraryManager.isBuiltIn(item.name());
            boolean cached = ShyneLibraryManager.isCached(item.name());
            String override = statusOverrides.get(item.name());

            Button actionBtn = Button.builder(Component.literal(override != null ? override : (builtIn ? "Built-in" : (cached ? "Cached" : "⬇ Download"))), b -> {
                if (!builtIn && !cached && override == null) {
                    downloadItem(item);
                }
            }).bounds(btnX, rowY + 11, btnWidth, 20).build();
            actionBtn.active = !builtIn && !cached && override == null;
            addRenderableWidget(actionBtn);
        }

        Button prevBtn = Button.builder(Component.literal("‹"), b -> { offset = Math.max(0, offset - MAX_VISIBLE_ROWS); rebuildWidgets(); })
            .bounds(listX, pagerY, 32, 20).build();
        prevBtn.active = offset > 0;
        addRenderableWidget(prevBtn);

        Button nextBtn = Button.builder(Component.literal("›"), b -> { offset += MAX_VISIBLE_ROWS; rebuildWidgets(); })
            .bounds(listX + 36, pagerY, 32, 20).build();
        nextBtn.active = offset + MAX_VISIBLE_ROWS < visibleItems.size();
        addRenderableWidget(nextBtn);

        Button backBtn = Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(panelX + panelWidth - 94, footerY, 80, 20).build();
        addRenderableWidget(backBtn);
    }

    private void filterItems() {
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        visibleItems = allItems.stream().filter(item ->
            query.isEmpty() || item.name().toLowerCase(Locale.ROOT).contains(query)
                || item.description().toLowerCase(Locale.ROOT).contains(query)
                || item.category().toLowerCase(Locale.ROOT).contains(query)
        ).toList();
    }

    private void openFolder() {
        Path dir = ShyneLibraryManager.getLibrariesDir();
        try {
            Files.createDirectories(dir);
            Blaze3D.openPath(dir);
        } catch (Exception ignored) {}
    }

    private void fetchRegistry() {
        if (loading) return;
        loading = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(REGISTRY_URL)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonArray libs = root.getAsJsonArray("libraries");
                    List<LibraryItem> loaded = new ArrayList<>();
                    for (JsonElement el : libs) {
                        JsonObject obj = el.getAsJsonObject();
                        if ("lua".equalsIgnoreCase(obj.get("language").getAsString())) {
                            loaded.add(new LibraryItem(
                                obj.get("id").getAsString(),
                                obj.get("name").getAsString().replace(".lua", ""),
                                obj.get("version").getAsString(),
                                obj.get("category").getAsString(),
                                obj.get("description").getAsString(),
                                obj.get("raw_url").getAsString(),
                                obj.has("cdn_url") ? obj.get("cdn_url").getAsString() : ""
                            ));
                        }
                    }
                    return loaded;
                }
            } catch (Exception ignored) {}
            return null;
        }).thenAccept(list -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    loading = false;
                    if (list != null && !list.isEmpty()) {
                        allItems = list;
                        filterItems();
                    }
                    rebuildWidgets();
                });
            }
        });
    }

    private void downloadItem(LibraryItem item) {
        statusOverrides.put(item.name(), "Downloading...");
        rebuildWidgets();
        ShyneLibraryManager.downloadLibrary(item.name(), item.rawUrl()).whenComplete((path, err) -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    if (err == null) {
                        statusOverrides.put(item.name(), "Installed!");
                    } else {
                        statusOverrides.put(item.name(), "Failed");
                    }
                    rebuildWidgets();
                });
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0D1424);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0x8841D7E5);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF41D7E5);

        graphics.text(this.font, Component.translatable("screen.shyne_core.cloud.lua_libs.title"), panelX + 14, panelY + 16, 0xFFF4F7FC, false);
        graphics.text(this.font, Component.literal("Hybrid Tier 3 Cache: .minecraft/shyne_creator/libs/"), panelX + 14, panelY + 30, 0xFF8193AA, false);

        int listX = panelX + 14;
        int listY = panelY + 74;
        int listWidth = panelWidth - 28;
        int maxRows = Math.min(MAX_VISIBLE_ROWS, visibleItems.size() - offset);

        for (int i = 0; i < maxRows; i++) {
            int rowY = listY + i * ROW_HEIGHT;
            LibraryItem item = visibleItems.get(offset + i);
            boolean builtIn = ShyneLibraryManager.isBuiltIn(item.name());
            boolean cached = ShyneLibraryManager.isCached(item.name());

            graphics.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT - 4, (i % 2 == 0) ? 0xFF121B2F : 0xFF0F1728);
            graphics.outline(listX, rowY, listWidth, ROW_HEIGHT - 4, 0x22FFFFFF);

            String title = item.name() + " v" + item.version() + " [" + item.category() + "]";
            int titleColor = builtIn ? 0xFF79D8B2 : (cached ? 0xFF41D7E5 : 0xFFE2E8F0);
            graphics.text(this.font, Component.literal(title), listX + 8, rowY + 6, titleColor, false);
            String desc = this.font.plainSubstrByWidth(item.description(), listWidth - 120);
            graphics.text(this.font, Component.literal(desc), listX + 8, rowY + 20, 0xFF8D9CAE, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (this.minecraft != null) this.minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}
