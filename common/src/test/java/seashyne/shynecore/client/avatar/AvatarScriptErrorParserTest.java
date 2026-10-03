package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import seashyne.shynecore.client.avatar.runtime.AvatarScriptErrorParser;

import static org.junit.jupiter.api.Assertions.*;

public class AvatarScriptErrorParserTest {

    @Test
    void testStandardLuaRuntimeError() {
        String msg = "avatar.lua:14: attempt to index nil global 'mymodel'\nstack traceback:\n\tavatar.lua:14: in function 'tick'";
        LuaError err = new LuaError(msg);
        var parsed = AvatarScriptErrorParser.parse(err, "avatar.lua");

        assertEquals("avatar.lua", parsed.file());
        assertEquals(14, parsed.line());
        assertEquals("attempt to index nil global 'mymodel'", parsed.cleanMessage());
        assertEquals("avatar.lua:14 - attempt to index nil global 'mymodel'", parsed.formatted());
    }

    @Test
    void testStandardLuaSyntaxError() {
        String msg = "[string \"script.lua\"]:25: unexpected symbol near 'end'";
        LuaError err = new LuaError(msg);
        var parsed = AvatarScriptErrorParser.parse(err, "main.lua");

        assertEquals("script.lua", parsed.file());
        assertEquals(25, parsed.line());
        assertEquals("unexpected symbol near 'end'", parsed.cleanMessage());
        assertEquals("script.lua:25 - unexpected symbol near 'end'", parsed.formatted());
    }

    @Test
    void testGenericErrorWithLine() {
        Exception err = new RuntimeException("Error occurred at line 42 in evaluation");
        var parsed = AvatarScriptErrorParser.parse(err, "custom.lua");

        assertEquals("custom.lua", parsed.file());
        assertEquals(42, parsed.line());
        assertTrue(parsed.formatted().contains("custom.lua:42"));
    }

    @Test
    void testFallbackWhenNoLineNumber() {
        Exception err = new RuntimeException("Connection reset");
        var parsed = AvatarScriptErrorParser.parse(err, "fallback.lua");

        assertEquals("fallback.lua", parsed.file());
        assertEquals(-1, parsed.line());
        assertEquals("Connection reset", parsed.cleanMessage());
        assertEquals("Connection reset", parsed.formatted());
    }
}
