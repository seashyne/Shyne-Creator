package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;
import seashyne.shynecore.client.avatar.bridge.AvatarActionBridge;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Figura Action Wheel Compatibility Tests")
final class FiguraActionWheelCompatibilityTest {

    @Test
    @DisplayName("AvatarAction stores 8-wedge slot index and custom textures correctly")
    void avatarActionStoresSlotAndTextures() {
        AtomicBoolean primaryRun = new AtomicBoolean(false);
        AtomicBoolean secondaryRun = new AtomicBoolean(false);
        AtomicInteger scrolledDir = new AtomicInteger(0);

        AvatarAction action = new AvatarAction(
            "test_act",
            "Main Title",
            "Action Description",
            "main",
            "spark",
            false,
            true,
            true,
            false,
            0xFF202636,
            0xFF38BDF8,
            0xFFEF4444,
            3, // slotIndex 0..7 (slot 4 in 1-based)
            "textures.custom",
            0, 0, 16, 16, 1.0f,
            () -> primaryRun.set(true),
            () -> secondaryRun.set(true),
            scrolledDir::set
        );

        assertEquals("test_act", action.id());
        assertEquals("Main Title", action.title());
        assertEquals("Action Description", action.description());
        assertEquals("main", action.page());
        assertEquals("spark", action.icon());
        assertFalse(action.localOnly());
        assertTrue(action.closeOnUse());
        assertTrue(action.isToggle());
        assertFalse(action.isToggled());
        assertEquals(3, action.slotIndex());
        assertEquals("textures.custom", action.texture());
        assertTrue(action.hasTexture());
        assertEquals(0, action.texU());
        assertEquals(0, action.texV());
        assertEquals(16, action.texW());
        assertEquals(16, action.texH());
        assertEquals(0xFF202636, action.color());
        assertEquals(0xFF38BDF8, action.hoverColor());
        assertEquals(0xFFEF4444, action.toggleColor());

        action.setToggled(true);
        assertTrue(action.isToggled());

        action.callback().run();
        assertTrue(primaryRun.get());

        action.secondaryCallback().run();
        assertTrue(secondaryRun.get());

        action.onScroll(-1);
        assertEquals(-1, scrolledDir.get());
    }

    @Test
    @DisplayName("AvatarState maintains multi-page action wheel registry with page navigation")
    void avatarStateMultiPageSupport() {
        AvatarState state = new AvatarState("test_avatar", "test_model", Path.of(""), false, Set.of(), Set.of());

        AvatarAction mainAction = new AvatarAction(
            "main_slot_0", "Page 1 Action", "Desc", "main", "icon",
            false, true, false, false, null, null, null,
            0, "", 0, 0, 0, 0, 1.0f,
            () -> {}, null, null
        );

        AvatarAction subAction = new AvatarAction(
            "sub_slot_5", "Subpage Action", "Desc", "sub_menu", "icon",
            false, true, false, false, null, null, null,
            5, "", 0, 0, 0, 0, 1.0f,
            () -> {}, null, null
        );

        state.registerAction(mainAction);
        state.registerAction(subAction);

        assertEquals("", state.activeActionPage());
        Map<String, List<AvatarAction>> pages = state.actionsByPage();
        assertTrue(pages.containsKey("main"));
        assertTrue(pages.containsKey("sub_menu"));

        List<AvatarAction> mainList = pages.get("main");
        assertEquals(1, mainList.size());
        assertEquals("main_slot_0", mainList.get(0).id());
        assertEquals(0, mainList.get(0).slotIndex());

        List<AvatarAction> subList = pages.get("sub_menu");
        assertEquals(1, subList.size());
        assertEquals("sub_slot_5", subList.get(0).id());
        assertEquals(5, subList.get(0).slotIndex());

        state.setActiveActionPage("sub_menu");
        assertEquals("sub_menu", state.activeActionPage());

        state.setActiveActionPage(null);
        assertEquals("", state.activeActionPage());
    }

    @Test
    @DisplayName("AvatarState supports primary texture overrides for skin and outfit swapping")
    void avatarStatePrimaryTextureOverride() {
        AvatarState state = new AvatarState("test_avatar", "test_model", Path.of(""), false, Set.of(), Set.of());
        assertEquals("", state.primaryTextureOverride());

        state.setPrimaryTextureOverride("textures.pink_alt");
        assertEquals("textures.pink_alt", state.primaryTextureOverride());

        state.clearPrimaryTextureOverride();
        assertEquals("", state.primaryTextureOverride());
    }

    @Test
    @DisplayName("AvatarActionBridge parses 22-arg registrations and executes callbacks via globals")
    void avatarActionBridgeParsesFullArgs() {
        AvatarState state = new AvatarState("test_avatar", "test_model", Path.of(""), false, Set.of(), Set.of());
        Globals globals = JsePlatform.standardGlobals();
        AvatarActionBridge.register(globals, state);

        AtomicBoolean primaryFired = new AtomicBoolean(false);
        AtomicBoolean secondaryFired = new AtomicBoolean(false);
        AtomicInteger scrolledDelta = new AtomicInteger(0);

        globals.set("test_primary", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override public LuaValue call() {
                primaryFired.set(true);
                return LuaValue.NIL;
            }
        });

        globals.set("test_secondary", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override public LuaValue call() {
                secondaryFired.set(true);
                return LuaValue.NIL;
            }
        });

        globals.set("test_scroll", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                scrolledDelta.set(arg.toint());
                return LuaValue.NIL;
            }
        });

        // Call _avatar_action_register with 22 arguments:
        // 1:id, 2:title, 3:desc, 4:page, 5:localOnly, 6:closeOnUse, 7:primaryCb, 8:icon, 9:secondaryCb,
        // 10:isToggle, 11:toggled, 12:color, 13:hoverColor, 14:slotIndex, 15:texture, 16:u, 17:v, 18:w, 19:h, 20:scale,
        // 21:toggleColor, 22:scrollCb
        LuaValue registerFn = globals.get("_avatar_action_register");
        Varargs args = LuaValue.varargsOf(new LuaValue[] {
            LuaString.valueOf("figura_action_1"),
            LuaString.valueOf("Emote 1"),
            LuaString.valueOf("Dance emote"),
            LuaString.valueOf("main"),
            LuaValue.FALSE,
            LuaValue.TRUE,
            globals.get("test_primary"),
            LuaString.valueOf("dance_icon"),
            globals.get("test_secondary"),
            LuaValue.TRUE,
            LuaValue.FALSE,
            LuaDouble.valueOf(0xFF38BDF8L),
            LuaDouble.valueOf(0xFF60A5FAL),
            LuaInteger.valueOf(2), // Slot 3 (0-based 2)
            LuaString.valueOf("textures.dance"),
            LuaInteger.valueOf(0), LuaInteger.valueOf(16), LuaInteger.valueOf(16), LuaInteger.valueOf(16),
            LuaDouble.valueOf(1.0),
            LuaDouble.valueOf(0xFFEF4444L),
            globals.get("test_scroll")
        });

        LuaValue registered = registerFn.invoke(args).arg1();
        assertTrue(registered.toboolean());

        List<AvatarAction> actions = state.actions();
        assertEquals(1, actions.size());
        AvatarAction action = actions.get(0);

        assertEquals("figura_action_1", action.id());
        assertEquals("Emote 1", action.title());
        assertEquals("dance_icon", action.icon());
        assertEquals(2, action.slotIndex());
        assertEquals("textures.dance", action.texture());
        assertEquals(0, action.texU());
        assertEquals(16, action.texV());
        assertEquals(16, action.texW());
        assertEquals(16, action.texH());
        assertEquals(0xFF38BDF8, action.color());
        assertEquals(0xFFEF4444, action.toggleColor());

        action.callback().run();
        assertTrue(primaryFired.get());

        action.secondaryCallback().run();
        assertTrue(secondaryFired.get());

        action.onScroll(1);
        assertEquals(1, scrolledDelta.get());
    }

    @Test
    @DisplayName("AvatarActionBridge page switching routes active page state correctly")
    void avatarActionBridgePageSwitching() {
        AvatarState state = new AvatarState("test_avatar", "test_model", Path.of(""), false, Set.of(), Set.of());
        Globals globals = JsePlatform.standardGlobals();
        AvatarActionBridge.register(globals, state);

        LuaValue setPageFn = globals.get("_avatar_action_set_page");
        LuaValue getPageFn = globals.get("_avatar_action_get_page");

        assertTrue(getPageFn.call().isnil());

        setPageFn.call(LuaString.valueOf("sub_emotes"));
        assertEquals("sub_emotes", getPageFn.call().tojstring());
        assertEquals("sub_emotes", state.activeActionPage());

        setPageFn.call(LuaValue.NIL);
        assertTrue(getPageFn.call().isnil());
        assertEquals("", state.activeActionPage());
    }

    @Test
    @DisplayName("Figura nested action wheel pages navigate and execute callbacks")
    void nestedActionWheelNavigation() throws Exception {
        AvatarState state = new AvatarState("test_avatar", "test_model", Path.of(""), false, Set.of(), Set.of());
        Globals globals = JsePlatform.standardGlobals();
        AvatarActionBridge.register(globals, state);

        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/avatar/63_figura_action_wheel.lua")) {
            assertNotNull(in);
            globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "63_figura_action_wheel.lua").call();
        }

        String luaScript = """
            local mainPage = action_wheel:newPage()
            local emotePage = action_wheel:newPage()
            local skinsPage = action_wheel:newPage()
            local dokiMPage = action_wheel:newPage()
            local settingsPage = action_wheel:newPage()
            action_wheel:setPage(mainPage)

            mainPage:newAction():title("Emotes"):onLeftClick(function() action_wheel:setPage(emotePage) end)
            mainPage:newAction():title("Skins"):onLeftClick(function() action_wheel:setPage(skinsPage) end)
            mainPage:newAction():title("Settings"):onLeftClick(function() action_wheel:setPage(settingsPage) end)

            emotePage:newAction():title("Return"):onLeftClick(function() action_wheel:setPage(mainPage) end)
            emotePage:newAction():title("Laugh"):onLeftClick(function() _laugh_called = true end)
        """;
        globals.load(luaScript, "test_script.lua").call();

        assertEquals("page_1", state.activeActionPage());
        Map<String, List<AvatarAction>> byPage = state.actionsByPage();
        assertTrue(byPage.containsKey("page_1"));
        assertTrue(byPage.containsKey("page_2"));
        assertEquals(3, byPage.get("page_1").size());
        assertEquals(2, byPage.get("page_2").size());

        // Click Emotes
        AvatarAction emotesAction = byPage.get("page_1").get(0);
        assertEquals("Emotes", emotesAction.title());
        emotesAction.callback().run();

        assertEquals("page_2", state.activeActionPage());

        // Click Laugh on page 2
        AvatarAction laughAction = byPage.get("page_2").get(1);
        assertEquals("Laugh", laughAction.title());
        laughAction.callback().run();
        assertTrue(globals.get("_laugh_called").toboolean());

        // Click Return on page 2
        AvatarAction returnAction = byPage.get("page_2").get(0);
        assertEquals("Return", returnAction.title());
        returnAction.callback().run();
        assertEquals("page_1", state.activeActionPage());
    }
}
