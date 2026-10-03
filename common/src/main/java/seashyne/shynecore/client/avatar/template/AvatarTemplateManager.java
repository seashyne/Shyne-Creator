package seashyne.shynecore.client.avatar.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Provides starter project templates for new avatars in Shyne Creator,
 * including minimal, figura-compatible, and action-wheel setups.
 */
public final class AvatarTemplateManager {

    public record TemplateInfo(String id, String displayName, String description, List<String> permissions) {}

    private static final List<TemplateInfo> TEMPLATES = List.of(
        new TemplateInfo("minimal", "Minimal Starter", "Lightweight clean starter with model and basic tick/render event hooks.", List.of()),
        new TemplateInfo("figura_compat", "Figura Compatible", "Ready for Figura creators with nameplates, pings, item/damage events, and safe data.", List.of("nameplate", "chat_nameplate", "tab_list_nameplate", "data", "network")),
        new TemplateInfo("action_wheel", "Action Wheel Showcase", "Interactive avatar with customizable action wheel pages and toggleable model parts.", List.of("chat_nameplate"))
    );

    private AvatarTemplateManager() {}

    public static List<TemplateInfo> listTemplates() {
        return TEMPLATES;
    }

    public static TemplateInfo findTemplate(String id) {
        if (id == null) return null;
        for (TemplateInfo t : TEMPLATES) {
            if (t.id().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    /**
     * Scaffolds a new avatar project directory from the chosen template.
     */
    public static Path scaffold(String templateId, Path targetDir, String avatarId, String avatarName) throws IOException {
        TemplateInfo template = findTemplate(templateId);
        if (template == null) {
            template = TEMPLATES.get(0); // fallback to minimal
        }

        String safeId = (avatarId == null || avatarId.isBlank())
            ? "my_avatar"
            : avatarId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        String safeName = (avatarName == null || avatarName.isBlank()) ? "My Avatar" : avatarName;

        Path avatarRoot = targetDir.resolve(safeId);
        if (!Files.exists(avatarRoot)) {
            Files.createDirectories(avatarRoot);
        }

        // 1. Write avatar.json
        String manifestJson = generateManifest(template, safeId, safeName);
        Files.writeString(avatarRoot.resolve("avatar.json"), manifestJson, StandardCharsets.UTF_8);

        // 2. Write model.bbmodel
        String modelContent = generateStarterModel(safeName);
        Files.writeString(avatarRoot.resolve("model.bbmodel"), modelContent, StandardCharsets.UTF_8);

        // 3. Write main.lua
        String scriptContent = generateStarterScript(template.id(), safeId, safeName);
        Files.writeString(avatarRoot.resolve("main.lua"), scriptContent, StandardCharsets.UTF_8);

        // 4. Write README.md
        String readmeContent = generateReadme(template, safeId, safeName);
        Files.writeString(avatarRoot.resolve("README.md"), readmeContent, StandardCharsets.UTF_8);

        return avatarRoot;
    }

    private static String generateManifest(TemplateInfo template, String id, String name) {
        StringBuilder perms = new StringBuilder();
        for (int i = 0; i < template.permissions().size(); i++) {
            perms.append("\"").append(template.permissions().get(i)).append("\"");
            if (i < template.permissions().size() - 1) perms.append(", ");
        }

        return String.format("""
            {
              "standard": "2.0",
              "id": "%s",
              "name": "%s",
              "version": "1.0.0",
              "model": "model.bbmodel",
              "main": "main.lua",
              "replace_vanilla": false,
              "permissions": [%s],
              "api": "2.0",
              "automatic_api": true
            }
            """, id, name, perms.toString());
    }

    private static String generateStarterModel(String name) {
        return """
            {
              "meta": {"format_version": "4.10", "model_format": "free"},
              "name": "%s",
              "resolution": {"width": 64, "height": 64},
              "elements": [
                {"name":"Body","uuid":"cube-body","type":"cube","from":[-4,0,-2],"to":[4,12,2],"origin":[0,6,0],"faces":{"north":{"uv":[0,0,8,12]},"south":{"uv":[0,0,8,12]},"east":{"uv":[0,0,4,12]},"west":{"uv":[0,0,4,12]},"up":{"uv":[0,0,8,4]},"down":{"uv":[0,0,8,4]}}},
                {"name":"Head","uuid":"cube-head","type":"cube","from":[-4,12,-4],"to":[4,20,4],"origin":[0,16,0],"faces":{"north":{"uv":[8,0,16,8]},"south":{"uv":[8,0,16,8]},"east":{"uv":[8,0,16,8]},"west":{"uv":[8,0,16,8]},"up":{"uv":[8,0,16,8]},"down":{"uv":[8,0,16,8]}}}
              ],
              "outliner": [
                {"name":"root","uuid":"bone-root","origin":[0,0,0],"children":["cube-body",{"name":"Head","uuid":"bone-head","origin":[0,12,0],"children":["cube-head"]}]}
              ],
              "animations": [
                {"name":"idle","length":2,"loop":"loop","animators":{"bone-root":{"name":"root","rotation":[{"time":0,"vector":[0,-2,0]},{"time":1,"vector":[0,2,0]},{"time":2,"vector":[0,-2,0]}]}}}
              ]
            }
            """.formatted(name);
    }

    private static String generateStarterScript(String templateId, String id, String name) {
        if ("figura_compat".equals(templateId)) {
            return """
                -- Shyne Avatar: Figura Compatibility Starter
                -- Supports Figura nameplates, typed pings, damage events, and safe data.

                print("[Avatar] " .. AVATAR_ID .. " (" .. SHYNE_API_VERSION .. ") initialized!")

                -- Nameplate 2.0 configuration
                if nameplate.ENTITY then
                    nameplate.ENTITY:setText("§b✦ " .. "%s" .. " §7[Creator]§r")
                    nameplate.ENTITY:setBadge("✦", 0x41D7E5)
                end

                -- Define typed synchronization ping
                pings:define("wave", {"string", "number"}, function(greeting, intensity)
                    print("[Ping] Wave received: " .. tostring(greeting) .. " with intensity " .. tostring(intensity))
                end)

                -- Item use event
                events.ITEM_USE:register(function(item, hand)
                    print("[Avatar] Used item in " .. tostring(hand))
                end)

                -- Entity damage event
                events.ENTITY_DAMAGE:register(function(amount, source, fatal)
                    print("[Avatar] Damaged by " .. tostring(amount) .. " from " .. tostring(source))
                end)

                -- Safe persistent data storage
                local loginCount = data:get("login_count", 0) + 1
                data:set("login_count", loginCount)
                data:save()
                print("[Avatar] Persistent login count: " .. tostring(loginCount))
                """.formatted(name);
        }

        if ("action_wheel".equals(templateId)) {
            return """
                -- Shyne Avatar: Action Wheel Starter
                print("[Avatar] " .. AVATAR_ID .. " Action Wheel ready!")

                local page = action_wheel.newPage()
                action_wheel.setPage(page)

                local hatAction = page:newAction()
                hatAction:setTitle("Toggle Hat")
                hatAction:setColor(0.25, 0.84, 0.90)

                local hatVisible = true
                hatAction:setOnLeftClick(function()
                    hatVisible = not hatVisible
                    if models.model and models.model.root and models.model.root.Head then
                        models.model.root.Head:setVisible(hatVisible)
                    end
                    print("[ActionWheel] Hat toggled: " .. tostring(hatVisible))
                end)
                """;
        }

        // Minimal starter
        return """
            -- Shyne Avatar: Minimal Starter
            print("[Avatar] " .. AVATAR_ID .. " loaded.")

            events.TICK:register(function()
                -- Put your tick update logic here
            end)

            events.RENDER:register(function(delta, context)
                -- Frame rendering hook
            end)
            """;
    }

    private static String generateReadme(TemplateInfo template, String id, String name) {
        return """
            # %s (%s)

            Generated by **Shyne Creator Starter Templates** (`%s`).

            ## Project Structure
            - `avatar.json`: Avatar manifest with permissions and metadata.
            - `model.bbmodel`: Blockbench 3D model containing geometry and animations.
            - `main.lua`: Avatar script with event hooks and runtime logic.

            ## Quick Tips
            1. Edit `model.bbmodel` directly in [Blockbench](https://www.blockbench.net/).
            2. Shyne's file watcher will automatically hot-reload your changes in-game!
            3. If you make a script syntax error, the file watcher will report the exact line number in chat.
            4. Inspect import reports and compatibility matrix at any time.
            """.formatted(name, id, template.id());
    }
}
