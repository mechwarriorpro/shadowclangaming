package com.shadowclangaming.lua;

/**
 * The entry point.
 *
 * Nothing outside this package should touch the parser, the compiler or the machine directly:
 *
 *   {@code Lua.newVm()}          a ready VM with the standard library installed
 *   {@code Lua.load(src, name)}  compile to a callable function
 *   {@code Lua.run(src, vm)}     compile and run to completion (tests and one-shot scripts)
 *
 * A block entity keeps the {@link Vm} and a paused script instead of calling {@code run} again, because a
 * machine has to survive a tick boundary in the middle of a loop.
 */
public final class Lua {

    private Lua() {
    }

    public static Vm newVm() {
        Vm vm = new Vm();
        Stdlib.install(vm);
        return vm;
    }

    /**
     * Compile source to a function.
     *
     * @throws LuaError with a {@code source:line:} prefix on any syntax error
     */
    public static LuaFunction load(String source, String name) {
        LuaFunction.Proto proto = Emitter.compile(source, name);
        return new LuaFunction(proto, new Cell[0]);
    }

    /** Compile and run, returning the results. Intended for tests and one-liners. */
    public static Object[] run(String source, Vm vm) {
        LuaFunction function = load(source, "=shell");
        vm.pushEntry(function, new Object[0]);
        vm.run(Integer.MAX_VALUE);
        return vm.lastResults();
    }

    /** Pretty error line for a machine's log: message plus the deepest frame it reached. */
    public static String describe(Throwable error, Vm vm) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        if (vm != null && !vm.stackTrace().isEmpty()) {
            return message + "  [" + vm.stackTrace() + "]";
        }
        return message;
    }
}
