package com.shadowclangaming.lua;

import java.util.ArrayList;
import java.util.List;

/**
 * Functions, prototypes and native builtins.
 *
 * A {@link Proto} is the compiled form of one Lua function: bytecode, constants, nested prototypes and the
 * descriptors a closure needs to capture its upvalues. A {@link LuaFunction} is a Proto plus the closed
 * cells for this particular instantiation, which is exactly how Lua itself models closures.
 */
public final class LuaFunction {

    public final Proto proto;
    public final Cell[] upvalues;

    public LuaFunction(Proto proto, Cell[] upvalues) {
        this.proto = proto;
        this.upvalues = upvalues;
    }

    /** A compiled Lua function. */
    public static final class Proto {
        public String name = "?";
        public String source = "=?";
        public int line = 1;
        public int params;
        public boolean vararg;
        public int maxSlots = 8;
        public final List<int[]> code = new ArrayList<>();
        public final List<Object> consts = new ArrayList<>();
        public final List<Proto> protos = new ArrayList<>();
        /** One entry per nested closure: {protoIndex, count, kind0, index0, kind1, index1, ...}. */
        public final List<int[]> closures = new ArrayList<>();

        public int params() {
            return params;
        }

        public int constIndex(Object value) {
            for (int i = 0; i < consts.size(); i++) {
                Object existing = consts.get(i);
                if (existing == null ? value == null : existing.equals(value)) {
                    return i;
                }
            }
            consts.add(value);
            return consts.size() - 1;
        }
    }

    /** Somebody else's Java code exposed to Lua. */
    public interface Native {
        Object[] call(Vm vm, Object[] args);
    }

    public static final class NativeFunction {
        public final String name;
        public final Native body;

        public NativeFunction(String name, Native body) {
            this.name = name;
            this.body = body;
        }

        @Override
        public String toString() {
            return "function: builtin " + name;
        }
    }
}
