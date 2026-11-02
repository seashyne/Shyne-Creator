package seashyne.shynecore.client.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import seashyne.shynecore.client.avatar.AvatarPermission;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UI Translation Completeness Tests")
final class TranslationCompletenessTest {

    private static JsonObject enUs;
    private static JsonObject thTh;

    @BeforeAll
    static void loadLanguages() {
        Gson gson = new Gson();
        try (InputStream enStream = TranslationCompletenessTest.class.getResourceAsStream("/assets/shyne_creator/lang/en_us.json")) {
            assertNotNull(enStream, "en_us.json should exist in assets");
            enUs = gson.fromJson(new InputStreamReader(enStream, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            fail("Failed to load en_us.json: " + e.getMessage());
        }

        try (InputStream thStream = TranslationCompletenessTest.class.getResourceAsStream("/assets/shyne_creator/lang/th_th.json")) {
            assertNotNull(thStream, "th_th.json should exist in assets");
            thTh = gson.fromJson(new InputStreamReader(thStream, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            fail("Failed to load th_th.json: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("All AvatarPermission values have valid name and description keys in en_us and th_th")
    void allAvatarPermissionsLocalized() {
        for (AvatarPermission perm : AvatarPermission.values()) {
            String nameKey = perm.translationKey();
            String descKey = perm.descriptionKey();

            assertTrue(enUs.has(nameKey), "en_us missing permission name key: " + nameKey);
            assertFalse(enUs.get(nameKey).getAsString().isBlank(), "en_us blank permission name: " + nameKey);

            assertTrue(enUs.has(descKey), "en_us missing permission description key: " + descKey);
            assertFalse(enUs.get(descKey).getAsString().isBlank(), "en_us blank permission description: " + descKey);

            assertTrue(thTh.has(nameKey), "th_th missing permission name key: " + nameKey);
            assertFalse(thTh.get(nameKey).getAsString().isBlank(), "th_th blank permission name: " + nameKey);

            assertTrue(thTh.has(descKey), "th_th missing permission description key: " + descKey);
            assertFalse(thTh.get(descKey).getAsString().isBlank(), "th_th blank permission description: " + descKey);
        }
    }

    @Test
    @DisplayName("All ShyneNavBar sections have valid labels and tooltips in en_us and th_th")
    void allNavBarSectionsLocalized() {
        for (ShyneNavBar.Section section : ShyneNavBar.Section.values()) {
            assertTrue(enUs.has(section.labelKey), "en_us missing nav bar label: " + section.labelKey);
            assertTrue(enUs.has(section.tooltipKey), "en_us missing nav bar tooltip: " + section.tooltipKey);

            assertTrue(thTh.has(section.labelKey), "th_th missing nav bar label: " + section.labelKey);
            assertTrue(thTh.has(section.tooltipKey), "th_th missing nav bar tooltip: " + section.tooltipKey);
        }
    }

    @Test
    @DisplayName("Core Studio UI and Action Wheel keys exist in en_us and th_th")
    void coreStudioKeysLocalized() {
        List<String> requiredKeys = List.of(
            "screen.shyne_core.action_wheel.title",
            "screen.shyne_core.action_wheel.back",
            "screen.shyne_core.action_wheel.right_click_back",
            "screen.shyne_core.action_wheel.center_back",
            "screen.shyne_core.action_wheel.center_close",
            "screen.shyne_core.action_wheel.mode_avatar",
            "screen.shyne_core.action_wheel.mode_controls",
            "screen.shyne_core.action_wheel.controls",
            "screen.shyne_core.action_wheel.settings_hint",
            "screen.shyne_core.permissions.local_title",
            "screen.shyne_core.permissions.safe_only",
            "screen.shyne_core.permissions.safe_only.tooltip",
            "screen.shyne_core.permissions.allow_all",
            "screen.shyne_core.permissions.allow_all.tooltip",
            "screen.shyne_core.permissions.deny_all",
            "screen.shyne_core.permissions.deny_all.tooltip",
            "screen.shyne_core.permissions.save_apply",
            "screen.shyne_core.permissions.standard_badge",
            "screen.shyne_core.permissions.sensitive_badge",
            "screen.shyne_core.permissions.allowed",
            "screen.shyne_core.permissions.blocked",
            "screen.shyne_core.permissions.unused",
            "screen.shyne_core.permissions.show_unused",
            "screen.shyne_core.permissions.hide_unused",
            "screen.shyne_core.avatars.importer",
            "screen.shyne_core.avatars.importer.tooltip",
            "screen.shyne_core.avatars.permissions",
            "screen.shyne_core.avatars.permissions.tooltip",
            "screen.shyne_core.avatars.wings",
            "screen.shyne_core.avatars.wings.tooltip",
            "screen.shyne_core.avatars.armor",
            "screen.shyne_core.avatars.armor.tooltip",
            "screen.shyne_core.avatars.preview_hint",
            "screen.shyne_core.pause_studio.tooltip"
        );

        for (String key : requiredKeys) {
            assertTrue(enUs.has(key), "en_us missing required key: " + key);
            assertFalse(enUs.get(key).getAsString().isBlank(), "en_us has blank value for: " + key);

            assertTrue(thTh.has(key), "th_th missing required key: " + key);
            assertFalse(thTh.get(key).getAsString().isBlank(), "th_th has blank value for: " + key);
        }
    }
}
