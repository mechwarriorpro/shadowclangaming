package com.shadowclangaming.lua;

/**
 * A Lua runtime error.
 *
 * Deliberately not a Minecraft class and not checked: the whole {@code lua} package has no Minecraft
 * imports at all, which is why the interpreter can be compiled and exercised by a plain JVM harness
 * (<code>tools/run_lua_harness.sh</code>) instead of only being testable by launching a game.
 */
public class LuaError extends RuntimeException {

    public LuaError(String message) {
        super(message);
    }

    public LuaError(String message, Throwable cause) {
        super(message, cause);
    }

    /** "attempt to index a nil value" and friends. */
    public static LuaError of(String format, Object... args) {
        return new LuaError(String.format(java.util.Locale.ROOT, format, args));
    }
}
