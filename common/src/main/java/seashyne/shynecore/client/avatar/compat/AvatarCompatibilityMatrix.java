package seashyne.shynecore.client.avatar.compat;

import seashyne.shynecore.client.avatar.AvatarPermission;

import java.util.*;

/**
 * Registry and generator for the comprehensive Figura & Shyne API Compatibility Matrix.
 * Outlines exact support tiers, required permissions, and migration notes for creators.
 */
public final class AvatarCompatibilityMatrix {

    public enum SupportStatus {
        FULL("🟢 Full", "Fully supported and identical to Figura standard."),
        RESTRICTED("🟡 Restricted", "Supported, but requires user-approved permission in avatar.json."),
        PARTIAL("🔵 Partial", "Supported with specific parameters or client-side sandbox boundaries."),
        UNSUPPORTED("🔴 Unsupported", "Blocked or unmapped for sandbox security or multiplayer integrity.");

        private final String badge;
        private final String description;

        SupportStatus(String badge, String description) {
            this.badge = badge;
            this.description = description;
        }

        public String badge() { return badge; }
        public String description() { return description; }
    }

    public enum Category {
        EVENTS("P1 Events"),
        MODEL_AND_RENDER("P2 Model & Render Contexts"),
        NAMEPLATE_2_0("P3 Nameplate 2.0"),
        SAFE_DATA_AND_JSON("P4 Safe Creator Data & JSON"),
        PERMISSIONS_AND_BUDGETS("P5 Permissions & Quotas"),
        NETWORK_AND_PINGS("P6 Network API & Typed Pings"),
        CREATOR_WORKFLOW("P7 Creator Workflow"),
        ACTION_WHEEL("Action Wheel"),
        PLAYER_AND_WORLD("Player & World Properties");

        private final String title;
        Category(String title) { this.title = title; }
        public String title() { return title; }
    }

    public record ApiEntry(
        Category category,
        String apiName,
        String signature,
        SupportStatus status,
        String requiredPermission,
        String description,
        String notes
    ) {}

    private static final List<ApiEntry> ENTRIES = new ArrayList<>();

    static {
        // P1 Events
        register(Category.EVENTS, "events.TICK", "events.TICK:register(fn)", SupportStatus.FULL, null, "Client tick event (20Hz)", "Runs every client tick");
        register(Category.EVENTS, "events.RENDER", "events.RENDER:register(fn(delta, ctx))", SupportStatus.FULL, null, "Frame render event", "Passes partial tick and render context string");
        register(Category.EVENTS, "events.POST_RENDER", "events.POST_RENDER:register(fn(delta, ctx))", SupportStatus.FULL, null, "Post entity render event", "Runs after entity meshes are rendered");
        register(Category.EVENTS, "events.WORLD_RENDER", "events.WORLD_RENDER:register(fn(delta))", SupportStatus.FULL, null, "World stage render hook", "Runs during world rendering phase");
        register(Category.EVENTS, "events.MOUSE_PRESS", "events.MOUSE_PRESS:register(fn(button, action, mods))", SupportStatus.FULL, null, "Mouse button press/release", "Fires on left/right/middle click");
        register(Category.EVENTS, "events.MOUSE_SCROLL", "events.MOUSE_SCROLL:register(fn(dx, dy))", SupportStatus.FULL, null, "Mouse wheel scroll event", "Smooth mouse scroll delta");
        register(Category.EVENTS, "events.KEY_PRESS", "events.KEY_PRESS:register(fn(key, action, mods))", SupportStatus.FULL, null, "Keyboard key events", "Fires for GLFW key codes");
        register(Category.EVENTS, "events.CHAR_TYPED", "events.CHAR_TYPED:register(fn(char, mods))", SupportStatus.FULL, null, "Character typed event", "Fires on character unicode entry");
        register(Category.EVENTS, "events.ITEM_USE", "events.ITEM_USE:register(fn(item, hand))", SupportStatus.FULL, null, "Player item use hook", "Fires on eating, bow pulling, weapon swing");
        register(Category.EVENTS, "events.CHAT_RECEIVE", "events.CHAT_RECEIVE:register(fn(message))", SupportStatus.FULL, null, "Chat message incoming event", "Fires on system and player chat messages");
        register(Category.EVENTS, "events.ENTITY_DAMAGE", "events.ENTITY_DAMAGE:register(fn(amount, src, fatal))", SupportStatus.FULL, null, "Entity damage taken event", "Fires when player receives damage");
        register(Category.EVENTS, "events.PROJECTILE_HIT", "events.PROJECTILE_HIT:register(fn(projectile, hitType))", SupportStatus.FULL, null, "Projectile collision event", "Fires when projectile impacts entity or block");
        register(Category.EVENTS, "events.TOTEM_POP", "events.TOTEM_POP:register(fn(hand))", SupportStatus.FULL, null, "Totem of Undying activation", "Fires on life-saving totem pops");

        // P2 Model & Render Contexts
        register(Category.MODEL_AND_RENDER, "models.<part>", "models.<part>", SupportStatus.FULL, null, "Hierarchy indexing", "Direct dot indexing of model groups and cubes");
        register(Category.MODEL_AND_RENDER, "part:setVisible", "part:setVisible(boolean)", SupportStatus.FULL, null, "Toggle part visibility", "Live hide/show bone or cube");
        register(Category.MODEL_AND_RENDER, "part:setRot", "part:setRot(x, y, z)", SupportStatus.FULL, null, "Bone rotation in degrees", "Live rotation transformation");
        register(Category.MODEL_AND_RENDER, "part:setPos", "part:setPos(x, y, z)", SupportStatus.FULL, null, "Bone translation in units", "Live position offset");
        register(Category.MODEL_AND_RENDER, "part:setScale", "part:setScale(x, y, z)", SupportStatus.FULL, null, "Bone scale factor", "Independent 3-axis scaling");
        register(Category.MODEL_AND_RENDER, "part:setColor", "part:setColor(r, g, b, [a])", SupportStatus.FULL, null, "Color tinting", "Multiplies vertex colors");
        register(Category.MODEL_AND_RENDER, "part:setMaterial", "part:setMaterial(materialName)", SupportStatus.FULL, null, "Material shader binding", "Dynamic Texture and shader binding");
        register(Category.MODEL_AND_RENDER, "part:setPrimaryTexture", "part:setPrimaryTexture(textureId)", SupportStatus.FULL, null, "Texture swap", "Binds dynamic or static texture");
        register(Category.MODEL_AND_RENDER, "dynamic_texture", "dynamic_texture:bindMaterial(mat, tex)", SupportStatus.FULL, null, "Dynamic texture binding", "Live programmatic canvas texture");
        register(Category.MODEL_AND_RENDER, "PORTRAIT context", "context == 'PORTRAIT'", SupportStatus.FULL, null, "GUI portrait rendering", "Invoked when rendering in inventory/HUD portrait");
        register(Category.MODEL_AND_RENDER, "SKULL context", "context == 'SKULL'", SupportStatus.FULL, null, "Skull block rendering", "Invoked when rendering as placed or held player skull");
        register(Category.MODEL_AND_RENDER, "renderer:setPostShader", "renderer:setPostShader(shader)", SupportStatus.RESTRICTED, "hud_render", "Post-processing shader", "Applies or clears Minecraft post-processing shader");
        register(Category.MODEL_AND_RENDER, "figuraMetatables", "figuraMetatables.<Type>", SupportStatus.FULL, null, "Figura metatable registry", "Provides Vector2, Vector3, Vector4, Matrix4, and ItemStack metatables");
        register(Category.MODEL_AND_RENDER, "client.setCameraPos", "client:setCameraPos(x, y, z)", SupportStatus.FULL, "camera", "Set camera position offset", "Forwards to camera.setPos and renderer:setCameraPos");

        // P3 Nameplate 2.0
        register(Category.NAMEPLATE_2_0, "nameplate.ENTITY", "nameplate.ENTITY:setText(str)", SupportStatus.FULL, null, "In-world overhead nameplate", "Overhead floating player nameplate");
        register(Category.NAMEPLATE_2_0, "nameplate.CHAT", "nameplate.CHAT:setText(str)", SupportStatus.RESTRICTED, "chat_nameplate", "Chat message prefix/nameplate", "Requires user approval for chat prefix");
        register(Category.NAMEPLATE_2_0, "nameplate.LIST", "nameplate.LIST:setText(str)", SupportStatus.RESTRICTED, "tab_list_nameplate", "Tab player list nameplate", "Requires user approval for tab list");
        register(Category.NAMEPLATE_2_0, "nameplate:setBadge", "nameplate:setBadge(icon, color)", SupportStatus.FULL, null, "Nameplate badge prefix", "Icon and hex color badge");
        register(Category.NAMEPLATE_2_0, "nameplate:setColor", "nameplate:setColor(r, g, b)", SupportStatus.FULL, null, "Nameplate text color", "RGB text coloring");
        register(Category.NAMEPLATE_2_0, "nameplate:setBackground", "nameplate:setBackground(color)", SupportStatus.FULL, null, "Nameplate background plate", "RGBA background fill");
        register(Category.NAMEPLATE_2_0, "nameplate:setOutline", "nameplate:setOutline(boolean)", SupportStatus.FULL, null, "Nameplate text outline", "Renders dark outer text outline");
        register(Category.NAMEPLATE_2_0, "nameplate:setShadow", "nameplate:setShadow(boolean)", SupportStatus.FULL, null, "Nameplate drop shadow", "Toggles font shadow offset");

        // P4 Safe Creator Data & JSON
        register(Category.SAFE_DATA_AND_JSON, "json.encode", "json.encode(table)", SupportStatus.FULL, null, "Safe JSON serializer", "Converts Lua tables to JSON string");
        register(Category.SAFE_DATA_AND_JSON, "json.decode", "json.decode(string)", SupportStatus.FULL, null, "Safe JSON deserializer", "Parses JSON string into Lua table");
        register(Category.SAFE_DATA_AND_JSON, "data:set", "data:set(key, value)", SupportStatus.RESTRICTED, "data", "Persistent avatar data storage", "Stores state scoped to avatar id");
        register(Category.SAFE_DATA_AND_JSON, "data:get", "data:get(key, [default])", SupportStatus.RESTRICTED, "data", "Retrieve persistent data", "Reads previously saved state");
        register(Category.SAFE_DATA_AND_JSON, "data:save", "data:save()", SupportStatus.RESTRICTED, "data", "Commit storage to disk", "Atomically saves storage to JSON file");
        register(Category.SAFE_DATA_AND_JSON, "data:load", "data:load()", SupportStatus.RESTRICTED, "data", "Reload storage from disk", "Reloads persistent storage");
        register(Category.SAFE_DATA_AND_JSON, "resources:has", "resources:has(path)", SupportStatus.FULL, null, "Safe asset existence check", "Checks file within avatar sandbox");
        register(Category.SAFE_DATA_AND_JSON, "resources:read", "resources:read(path)", SupportStatus.FULL, null, "Safe asset text reader", "Reads text file within avatar sandbox");
        register(Category.SAFE_DATA_AND_JSON, "io (sandboxed filesystem)", "io.open, io.lines, file:read, file:write", SupportStatus.FULL, "data", "Sandboxed avatar filesystem I/O", "Full Lua file I/O strictly sandboxed inside avatar directory");
        register(Category.SAFE_DATA_AND_JSON, "os (sandboxed environment & time)", "os.time, os.date, os.clock, os.getenv", SupportStatus.FULL, null, "Safe timers, dates, clocks, and environment", "Returns safe system timestamps, formatted dates, and avatar environment variables");

        // P5 Permissions & Budgets
        register(Category.PERMISSIONS_AND_BUDGETS, "avatar:hasPermission", "avatar:hasPermission(perm)", SupportStatus.FULL, null, "Query granted permission", "Checks if user approved permission");
        register(Category.PERMISSIONS_AND_BUDGETS, "AvatarPermissionScreen", "In-game GUI", SupportStatus.FULL, null, "Granular permissions screen", "User approval UI with toggleable permissions");
        register(Category.PERMISSIONS_AND_BUDGETS, "Dynamic Texture Quota", "512x512 edge, 16 MiB max", SupportStatus.FULL, null, "Texture budget enforcement", "Rejects oversized allocations gracefully");
        register(Category.PERMISSIONS_AND_BUDGETS, "Particle Quota", "64 particles/sec max", SupportStatus.FULL, null, "Particle rate limit", "Drops particles beyond rate budget");
        register(Category.PERMISSIONS_AND_BUDGETS, "Sound Quota", "16 sounds/sec max", SupportStatus.FULL, null, "Sound rate limit", "Drops sounds beyond rate budget");
        register(Category.PERMISSIONS_AND_BUDGETS, "Render Task Quota", "128 tasks max", SupportStatus.FULL, null, "Render tasks budget", "Prevents infinite render loops");
        register(Category.PERMISSIONS_AND_BUDGETS, "AvatarProfiler", "profiler:getMetrics()", SupportStatus.FULL, null, "Performance profiler", "Detailed timing breakdown of Lua & rendering");

        // P6 Network API & Typed Pings
        register(Category.NETWORK_AND_PINGS, "pings:define", "pings:define(name, schema, fn)", SupportStatus.FULL, null, "Typed network pings", "Automatic type coercion and bounds checking");
        register(Category.NETWORK_AND_PINGS, "pings.<name>", "pings.<name>(...args)", SupportStatus.FULL, null, "Invoke network ping", "Sends synchronized ping to clients");
        register(Category.NETWORK_AND_PINGS, "network.send", "network.send(channel, payload)", SupportStatus.RESTRICTED, "network", "Managed packet channel", "Sends message over server-approved channel");
        register(Category.NETWORK_AND_PINGS, "network.on", "network.on(channel, fn(payload))", SupportStatus.RESTRICTED, "network", "Listen to packet channel", "Receives messages from packet channel");
        register(Category.NETWORK_AND_PINGS, "network.allow_channel", "network.allow_channel(channel)", SupportStatus.RESTRICTED, "network", "Whitelist custom channel", "Allows channel for communication");
        register(Category.NETWORK_AND_PINGS, "raw server_packets", "server_packets.raw(channel, data)", SupportStatus.FULL, "network", "Raw server packet access", "Dispatches raw payload data safely through Shyne channels");

        // P7 Creator Workflow
        register(Category.CREATOR_WORKFLOW, "Line-precise Hot Reload", "AvatarFileWatcher", SupportStatus.FULL, null, "Auto-reloads on file save", "Reports exact filename and line number on error");
        register(Category.CREATOR_WORKFLOW, "AvatarImportReport", "AvatarImporter.analyze()", SupportStatus.FULL, null, "Blockbench/Figura import report", "Metrics for cubes, bones, textures, and easing");
        register(Category.CREATOR_WORKFLOW, "AvatarScriptLinter", "AvatarScriptLinter.lint()", SupportStatus.FULL, null, "Automated API and permission linter", "Detects syntax errors, blocked calls, missing perms");
        register(Category.CREATOR_WORKFLOW, "AvatarTemplateManager", "AvatarTemplateManager.scaffold()", SupportStatus.FULL, null, "Starter project templates", "Minimal, Figura Compat, and Action Wheel templates");

        // Action Wheel
        register(Category.ACTION_WHEEL, "action_wheel.newPage", "action_wheel.newPage()", SupportStatus.FULL, null, "Create action wheel page", "Multi-page radial action menu");
        register(Category.ACTION_WHEEL, "action_wheel.setPage", "action_wheel.setPage(page)", SupportStatus.FULL, null, "Set active action page", "Displays page on radial wheel");
        register(Category.ACTION_WHEEL, "page:newAction", "page:newAction()", SupportStatus.FULL, null, "Add action to page", "Action button with icon, title, and callback");

        // Player & World Properties
        register(Category.PLAYER_AND_WORLD, "player:getName", "player:getName()", SupportStatus.FULL, null, "Player username", "Returns player name");
        register(Category.PLAYER_AND_WORLD, "player:getPos", "player:getPos()", SupportStatus.FULL, null, "Player world coordinates", "Returns vector3 position");
        register(Category.PLAYER_AND_WORLD, "player:getRot", "player:getRot()", SupportStatus.FULL, null, "Player pitch & yaw", "Returns vector2 rotation");
        register(Category.PLAYER_AND_WORLD, "player:getHealth", "player:getHealth()", SupportStatus.FULL, null, "Player health points", "Returns current health");
        register(Category.PLAYER_AND_WORLD, "player:isSneaking", "player:isSneaking()", SupportStatus.FULL, null, "Crouch state", "Checks if player is sneaking");
        register(Category.PLAYER_AND_WORLD, "world.getTime", "world.getTime()", SupportStatus.FULL, null, "Day/night time", "Read-only world time");
        register(Category.PLAYER_AND_WORLD, "world.setBlock", "world.setBlock(pos, block)", SupportStatus.RESTRICTED, "world_edit", "Modify world block", "Modifies world block via command and client level preview");
        register(Category.PLAYER_AND_WORLD, "world.setTime", "world.setTime(time)", SupportStatus.RESTRICTED, "world_edit", "Set world daytime", "Synchronizes daytime ticks via server command");
        register(Category.PLAYER_AND_WORLD, "host:sendChat", "host:sendChat(message)", SupportStatus.RESTRICTED, "command", "Send player chat or command", "Executes client player chat message or command");
    }

    private static void register(Category category, String apiName, String signature, SupportStatus status, String requiredPermission, String description, String notes) {
        ENTRIES.add(new ApiEntry(category, apiName, signature, status, requiredPermission, description, notes));
    }

    public static List<ApiEntry> allEntries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<ApiEntry> byCategory(Category category) {
        return ENTRIES.stream().filter(e -> e.category() == category).toList();
    }

    public static List<ApiEntry> byStatus(SupportStatus status) {
        return ENTRIES.stream().filter(e -> e.status() == status).toList();
    }

    /**
     * Generates a comprehensive markdown document detailing the complete compatibility matrix.
     */
    public static String generateMarkdownDocument() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Shyne Creator API Compatibility Matrix\n\n");
        sb.append("This document outlines the exact compatibility and support tiers of the **Shyne Creator MultiLoader** (Fabric + NeoForge on Minecraft 26.3 / Java 25) compared to Figura and Blockbench.\n\n");

        sb.append("## Support Tiers\n\n");
        for (SupportStatus status : SupportStatus.values()) {
            sb.append("- **").append(status.badge()).append("**: ").append(status.description()).append("\n");
        }
        sb.append("\n---\n\n");

        for (Category cat : Category.values()) {
            List<ApiEntry> catEntries = byCategory(cat);
            if (catEntries.isEmpty()) continue;

            sb.append("## ").append(cat.title()).append("\n\n");
            sb.append("| API / Function | Signature | Support Tier | Required Permission | Description | Notes |\n");
            sb.append("|---|---|---|---|---|---|\n");
            for (ApiEntry entry : catEntries) {
                String perm = entry.requiredPermission() != null ? "`" + entry.requiredPermission() + "`" : "—";
                sb.append("| `").append(entry.apiName()).append("` | `")
                  .append(entry.signature().replace("|", "\\|")).append("` | ")
                  .append(entry.status().badge()).append(" | ")
                  .append(perm).append(" | ")
                  .append(entry.description()).append(" | ")
                  .append(entry.notes()).append(" |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
