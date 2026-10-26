package com.shadowclangaming.lua;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The virtual machine.
 *
 * Three properties matter more than speed here, and all three come from keeping call frames explicit
 * instead of using Java recursion:
 *
 *   1. **It gives the tick back.** {@link #run(int)} executes at most N instructions and then returns
 *      {@link Status#PAUSED} with the whole call stack intact. A script with an infinite loop costs a
 *      server owner a slice of one tick and nothing else - it cannot hang the server, because the server
 *      decides how much to run.
 *   2. **Coroutines are free.** A coroutine is one of these VMs, paused. {@code resume} is
 *      {@link #run}, {@code yield} is a pause that hands values out.
 *   3. **Waiting is not blocking.** A native can throw {@link Yield} to suspend the whole script; the
 *      machine keeps the paused VM and resumes it later. That is what OpenComputers' {@code event.pull}
 *      means, and it is why an OS like MineOS can run at all on a tick-driven server.
 */
public final class Vm {

    /** What {@link #run(int)} did. */
    public enum Status {
        DONE,
        PAUSED
    }

    /** Thrown by a native that wants to wait for something. The script resumes where it left off. */
    public static final class Yield extends RuntimeException {
        public final String waitingFor;
        public final long timeout;
        /** Values handed out by {@code coroutine.yield}; null when waiting for an event instead. */
        public final Object[] values;

        public Yield(String waitingFor, long timeout) {
            this(waitingFor, timeout, null);
        }

        public Yield(String waitingFor, long timeout, Object[] values) {
            super("waiting for " + waitingFor, null, false, false);
            this.waitingFor = waitingFor;
            this.timeout = timeout;
            this.values = values;
        }

        /** A yield with values, as coroutine.yield does. */
        public static Yield of(Object[] values) {
            return new Yield("yield", 0L, values);
        }
    }

    /** One activation record. */
    public static final class Frame {
        final LuaFunction.Proto proto;
        final Cell[] slots;
        final Cell[] upvalues;
        final Object[] varargs;
        private Object[] stack = new Object[32];
        int sp;
        int pc;
        int wanted;
        int multi;

        Frame(LuaFunction.Proto proto, Cell[] upvalues, Object[] varargs, int wanted) {
            this.proto = proto;
            this.slots = new Cell[Math.max(1, proto.maxSlots)];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = new Cell(null);
            }
            this.upvalues = upvalues;
            this.varargs = varargs;
            this.wanted = wanted;
        }

        void push(Object value) {
            if (sp == stack.length) {
                Object[] bigger = new Object[stack.length * 2];
                System.arraycopy(stack, 0, bigger, 0, sp);
                stack = bigger;
            }
            stack[sp++] = value;
        }

        Object pop() {
            return sp > 0 ? stack[--sp] : null;
        }

        Object peek(int back) {
            int index = sp - 1 - back;
            return index >= 0 ? stack[index] : null;
        }

        /** Drop everything above index {@code sp - count}. */
        void trim(int count) {
            for (int i = 0; i < count && sp > 0; i++) {
                stack[--sp] = null;
            }
        }
    }

    private final Deque<Frame> frames = new ArrayDeque<>();
    /** Globals are shared between a machine and its coroutines. */
    public LuaTable globals = new LuaTable();
    /** Where {@code print} goes. A machine points this at its own log. */
    public java.util.function.Consumer<String> out = line -> {
    };

    private long executed;
    private int syncDepth = -1;
    private Object[] syncResults;
    private int pendingWanted;

    public long executed() {
        return executed;
    }

    /** Results of the most recent completed call, for coroutines and the harness. */
    public Object[] lastResults() {
        return syncResults == null ? new Object[0] : syncResults;
    }

    // ------------------------------------------------------------------ running

    public Status run(int budget) {
        long executedHere = 0;
        while (!frames.isEmpty()) {
            if (executedHere >= budget) {
                return Status.PAUSED;
            }
            Frame f = frames.peek();
            if (f.pc >= f.proto.code.size()) {
                returnFrame(f, new Object[0]);
                continue;
            }
            int[] ins = f.proto.code.get(f.pc++);
            executed++;
            executedHere++;
            switch (ins[0]) {
                case Op.PUSH_CONST -> f.push(f.proto.consts.get(ins[1]));
                case Op.PUSH_NIL -> f.push(null);
                case Op.PUSH_TRUE -> f.push(Boolean.TRUE);
                case Op.PUSH_FALSE -> f.push(Boolean.FALSE);
                case Op.DUP -> f.push(f.peek(0));
                case Op.POP -> f.pop();
                case Op.LOAD_LOCAL -> f.push(f.slots[ins[1]].value);
                case Op.STORE_LOCAL -> f.slots[ins[1]].value = f.pop();
                case Op.LOAD_UPVAL -> f.push(f.upvalues[ins[1]].value);
                case Op.STORE_UPVAL -> f.upvalues[ins[1]].value = f.pop();
                case Op.LOAD_GLOBAL -> f.push(globals.get(f.proto.consts.get(ins[1])));
                case Op.STORE_GLOBAL -> globals.set(f.proto.consts.get(ins[1]), f.pop());
                case Op.VARARG -> {
                    for (int i = 0; i < ins[1]; i++) {
                        f.push(i < f.varargs.length ? f.varargs[i] : null);
                    }
                }
                case Op.VARARG_ALL -> {
                    for (Object value : f.varargs) {
                        f.push(value);
                    }
                    f.multi = f.varargs.length;
                }
                case Op.NEW_TABLE -> {
                    int arrays = ins[1];
                    Object[] values = new Object[arrays];
                    for (int i = arrays - 1; i >= 0; i--) {
                        values[i] = f.pop();
                    }
                    LuaTable table = new LuaTable();
                    for (int i = 0; i < arrays; i++) {
                        table.set((double) (i + 1), values[i]);
                    }
                    f.push(table);
                }
                case Op.GET_INDEX -> {
                    Object key = f.pop();
                    Object table = f.pop();
                    f.push(index(table, key));
                }
                case Op.SET_INDEX -> {
                    Object value = f.pop();
                    Object key = f.pop();
                    Object table = f.pop();
                    setIndex(table, key, value);
                }
                case Op.GET_FIELD -> {
                    Object table = f.pop();
                    f.push(index(table, f.proto.consts.get(ins[1])));
                }
                case Op.SET_FIELD -> {
                    Object value = f.pop();
                    Object table = f.pop();
                    setIndex(table, f.proto.consts.get(ins[1]), value);
                }
                case Op.ADD -> arithmetic(f, "+");
                case Op.SUB -> arithmetic(f, "-");
                case Op.MUL -> arithmetic(f, "*");
                case Op.DIV -> arithmetic(f, "/");
                case Op.MOD -> arithmetic(f, "%");
                case Op.POW -> arithmetic(f, "^");
                case Op.NEG -> f.push(-toNumber(f.pop(), "perform arithmetic on"));
                case Op.NOT -> f.push(!truthy(f.pop()));
                case Op.LEN -> {
                    Object value = f.pop();
                    if (value instanceof String text) {
                        f.push((double) text.length());
                    } else if (value instanceof LuaTable table) {
                        f.push((double) table.length());
                    } else {
                        throw new LuaError("attempt to get length of a " + typeName(value) + " value");
                    }
                }
                case Op.CONCAT -> {
                    int count = ins[1];
                    Object[] parts = new Object[count];
                    for (int i = count - 1; i >= 0; i--) {
                        parts[i] = f.pop();
                    }
                    StringBuilder builder = new StringBuilder();
                    for (Object part : parts) {
                        builder.append(concatString(part));
                    }
                    f.push(builder.toString());
                }
                case Op.EQ -> {
                    Object right = f.pop();
                    Object left = f.pop();
                    f.push(equal(left, right));
                }
                case Op.NE -> {
                    Object right = f.pop();
                    Object left = f.pop();
                    f.push(!equal(left, right));
                }
                case Op.LT -> compare(f, -1);
                case Op.LE -> compare(f, 0);
                case Op.GT -> compare(f, 1);
                case Op.GE -> compare(f, 2);
                case Op.JMP -> f.pc = ins[1];
                case Op.JMP_FALSE -> {
                    if (!truthy(f.pop())) {
                        f.pc = ins[1];
                    }
                }
                case Op.JMP_FALSE_KEEP -> {
                    if (!truthy(f.peek(0))) {
                        f.pc = ins[1];
                    }
                }
                case Op.JMP_TRUE_KEEP -> {
                    if (truthy(f.peek(0))) {
                        f.pc = ins[1];
                    }
                }
                case Op.CALL, Op.CALL_MULTI, Op.CALL_ALL -> {
                    int argCount = ins[1];
                    int wanted = ins[0] == Op.CALL ? 1 : ins[0] == Op.CALL_ALL ? -1 : ins[2];
                    Object[] args = new Object[argCount];
                    for (int i = argCount - 1; i >= 0; i--) {
                        args[i] = f.pop();
                    }
                    Object function = f.pop();
                    invoke(f, function, args, wanted);
                }
                case Op.METHOD_CALL, Op.METHOD_CALL_MULTI -> {
                    int argCount = ins[1];
                    int wanted = ins[0] == Op.METHOD_CALL ? 1 : ins[3];
                    Object receiver = f.peek(argCount);
                    Object name = f.proto.consts.get(ins[2]);
                    Object function = index(receiver, name);
                    Object[] args = new Object[argCount + 1];
                    args[0] = receiver;
                    for (int i = argCount; i >= 1; i--) {
                        args[i] = f.pop();
                    }
                    f.pop(); // the receiver itself
                    invoke(f, function, args, wanted);
                }
                case Op.CALL_TAIL -> {
                    int argCount = ins[1] + f.multi;
                    f.multi = 0;
                    Object[] args = new Object[argCount];
                    for (int i = argCount - 1; i >= 0; i--) {
                        args[i] = f.pop();
                    }
                    invoke(f, f.pop(), args, 1);
                }
                case Op.METHOD_CALL_TAIL -> {
                    int argCount = ins[1] + f.multi;
                    f.multi = 0;
                    Object receiver = f.peek(argCount);
                    Object function = index(receiver, f.proto.consts.get(ins[2]));
                    Object[] args = new Object[argCount + 1];
                    args[0] = receiver;
                    for (int i = argCount; i >= 1; i--) {
                        args[i] = f.pop();
                    }
                    f.pop();
                    invoke(f, function, args, 1);
                }
                case Op.STORE_SLOTS -> {
                    int base = ins[1];
                    int count = ins[2];
                    int available = f.multi;
                    f.multi = 0;
                    for (int i = count - 1; i >= 0; i--) {
                        f.slots[base + i].value = i < available ? f.pop() : null;
                    }
                    for (int i = count; i < available; i++) {
                        f.pop();
                    }
                }
                case Op.APPEND_MULTI -> {
                    int start = ins[1];
                    int count = f.multi;
                    f.multi = 0;
                    Object[] values = new Object[count];
                    for (int i = count - 1; i >= 0; i--) {
                        values[i] = f.pop();
                    }
                    Object table = f.pop();
                    if (table instanceof LuaTable luaTable) {
                        for (int i = 0; i < count; i++) {
                            luaTable.set((double) (start + i), values[i]);
                        }
                    }
                }
                case Op.RETURN -> {
                    int count = ins[1];
                    Object[] values = new Object[count];
                    for (int i = count - 1; i >= 0; i--) {
                        values[i] = f.pop();
                    }
                    returnFrame(f, values);
                    if (syncDepth >= 0 && frames.size() == syncDepth) {
                        return Status.DONE;
                    }
                }
                case Op.RETURN_NONE -> {
                    returnFrame(f, new Object[0]);
                    if (syncDepth >= 0 && frames.size() == syncDepth) {
                        return Status.DONE;
                    }
                }
                case Op.RETURN_MULTI -> {
                    int count = f.multi + ins[1];
                    Object[] values = new Object[Math.max(0, count)];
                    for (int i = count - 1; i >= 0; i--) {
                        values[i] = f.pop();
                    }
                    returnFrame(f, values);
                    if (syncDepth >= 0 && frames.size() == syncDepth) {
                        return Status.DONE;
                    }
                }
                case Op.MAKE_CLOSURE -> {
                    int[] spec = f.proto.closures.get(ins[1]);
                    LuaFunction.Proto child = f.proto.protos.get(spec[0]);
                    int count = spec[1];
                    Cell[] upvalues = new Cell[count];
                    for (int i = 0; i < count; i++) {
                        int kind = spec[2 + i * 2];
                        int index = spec[3 + i * 2];
                        upvalues[i] = kind == 0 ? f.slots[index] : f.upvalues[index];
                    }
                    f.push(new LuaFunction(child, upvalues));
                }
                case Op.FOR_NUM_INIT -> {
                    int base = ins[1];
                    double start = toNumber(f.slots[base].value, "convert initial value");
                    double limit = toNumber(f.slots[base + 1].value, "convert limit");
                    double step = toNumber(f.slots[base + 2].value, "convert step");
                    boolean runs = step > 0 ? start <= limit : step < 0 && start >= limit;
                    if (!runs) {
                        f.pc = ins[2];
                    } else {
                        f.slots[base + 3].value = start;
                    }
                }
                case Op.FOR_NUM_NEXT -> {
                    int base = ins[1];
                    double limit = toNumber(f.slots[base + 1].value, "convert limit");
                    double step = toNumber(f.slots[base + 2].value, "convert step");
                    double next = toNumber(f.slots[base + 3].value, "convert loop variable") + step;
                    boolean runs = step > 0 ? next <= limit : next >= limit;
                    if (runs) {
                        f.slots[base + 3].value = next;
                        f.pc = ins[2];
                    }
                }
                case Op.FOR_GEN_INIT -> {
                    int base = ins[1];
                    int names = ins[3];
                    Object[] results = callNow(f.slots[base].value,
                            new Object[]{f.slots[base + 1].value, f.slots[base + 2].value});
                    if (results.length == 0 || results[0] == null) {
                        f.pc = ins[2];
                    } else {
                        f.slots[base + 2].value = results[0];
                        for (int i = 0; i < names; i++) {
                            f.slots[base + 3 + i].value = i < results.length ? results[i] : null;
                        }
                    }
                }
                case Op.FOR_GEN_NEXT -> {
                    int base = ins[1];
                    int names = ins[3];
                    Object control = f.slots[base + 2].value;
                    Object[] results = callNow(f.slots[base].value,
                            new Object[]{f.slots[base + 1].value, control});
                    if (results.length == 0 || results[0] == null) {
                        // done: fall through to after the loop
                    } else {
                        f.slots[base + 2].value = results[0];
                        for (int i = 0; i < names; i++) {
                            f.slots[base + 3 + i].value = i < results.length ? results[i] : null;
                        }
                        f.pc = ins[2];
                    }
                }
                default -> throw new LuaError("bad instruction " + ins[0]);
            }
        }
        return Status.DONE;
    }

    /**
     * Drop frames until the stack is back to {@code depth}.
     *
     * Needed whenever Java catches a script error: the throw unwinds Java, not the Lua frames, so a native
     * like {@code pcall} has to unwind them itself or the machine would carry on inside the half-finished
     * function it just rescued the caller from.
     */
    public void unwindTo(int depth) {
        while (frames.size() > depth) {
            frames.pop();
        }
        syncResults = null;
    }

    // ------------------------------------------------------------------ calls

    private void invoke(Frame caller, Object function, Object[] args, int wanted) {
        if (function instanceof LuaFunction lua) {
            Frame frame = new Frame(lua.proto, lua.upvalues, varargsFor(lua, args), wanted);
            int params = lua.proto.params;
            for (int i = 0; i < params; i++) {
                frame.slots[i].value = i < args.length ? args[i] : null;
            }
            frames.push(frame);
            return;
        }
        if (function instanceof LuaFunction.NativeFunction nativeFunction) {
            pendingWanted = wanted;
            Object[] results;
            try {
                results = nativeFunction.body.call(this, args);
            } catch (Yield yield) {
                // The caller's stack is already clean; the values come back through resumeWith.
                pendingWanted = wanted;
                throw yield;
            }
            pushResults(caller, results, wanted);
            return;
        }
        throw new LuaError("attempt to call a " + typeName(function) + " value");
    }

    private Object[] varargsFor(LuaFunction function, Object[] args) {
        if (!function.proto.vararg) {
            return new Object[0];
        }
        int params = function.proto.params();
        if (args.length <= params) {
            return new Object[0];
        }
        Object[] varargs = new Object[args.length - params];
        System.arraycopy(args, params, varargs, 0, varargs.length);
        return varargs;
    }

    private void pushResults(Frame frame, Object[] results, int wanted) {
        if (wanted < 0) {
            for (Object value : results) {
                frame.push(value);
            }
            frame.multi = results.length;
            return;
        }
        for (int i = 0; i < wanted; i++) {
            frame.push(i < results.length ? results[i] : null);
        }
    }

    private void returnFrame(Frame frame, Object[] results) {
        frames.pop();
        if (frames.isEmpty()) {
            syncResults = results;
            return;
        }
        if (syncDepth >= 0 && frames.size() == syncDepth) {
            syncResults = results;
            return;
        }
        pushResults(frames.peek(), results, frame.wanted);
    }

    /** A call that must finish before the caller continues: {@code pcall}, iterators, native helpers. */
    public Object[] callNow(Object function, Object[] args) {
        if (function instanceof LuaFunction.NativeFunction nativeFunction) {
            return nativeFunction.body.call(this, args);
        }
        if (!(function instanceof LuaFunction lua)) {
            throw new LuaError("attempt to call a " + typeName(function) + " value");
        }
        int depth = frames.size();
        int previousSync = syncDepth;
        Object[] previousResults = syncResults;
        syncDepth = depth;
        syncResults = null;
        try {
            Frame frame = new Frame(lua.proto, lua.upvalues, varargsFor(lua, args), -2);
            for (int i = 0; i < lua.proto.params(); i++) {
                frame.slots[i].value = i < args.length ? args[i] : null;
            }
            frames.push(frame);
            run(Integer.MAX_VALUE);
            return syncResults == null ? new Object[0] : syncResults;
        } finally {
            syncDepth = previousSync;
            syncResults = previousResults;
        }
    }

    /** Convenience for Java callers: {@code vm.call(fn, a, b)}. */
    public Object[] call(Object function, Object... args) {
        return callNow(function, args);
    }

    /** Hand a waiting native its values and continue. */
    public void resumeWith(Object... values) {
        Frame frame = frames.peek();
        if (frame == null) {
            return;
        }
        Object[] results = values == null ? new Object[0] : values;
        if (pendingWanted < 0) {
            for (Object value : results) {
                frame.push(value);
            }
            frame.multi = results.length;
        } else {
            for (int i = 0; i < pendingWanted; i++) {
                frame.push(i < results.length ? results[i] : null);
            }
        }
        pendingWanted = 0;
    }

    /** On a yield the caller's stack was already cleaned; make sure the results land once. */
    void markResumed() {
        pendingWanted = 0;
    }

    public boolean paused() {
        return !frames.isEmpty();
    }

    public int depth() {
        return frames.size();
    }

    public void pushEntry(LuaFunction function, Object[] args) {
        Frame frame = new Frame(function.proto, function.upvalues, varargsFor(function, args), -2);
        for (int i = 0; i < function.proto.params(); i++) {
            frame.slots[i].value = i < args.length ? args[i] : null;
        }
        frames.push(frame);
    }

    public void reset() {
        frames.clear();
        syncDepth = -1;
        syncResults = null;
        pendingWanted = 0;
    }

    public String stackTrace() {
        List<String> lines = new ArrayList<>();
        for (Frame frame : frames) {
            lines.add(frame.proto.name + "@" + frame.pc);
        }
        return String.join(" <- ", lines);
    }

    // ------------------------------------------------------------------ value helpers

    public static boolean truthy(Object value) {
        return value != null && !Boolean.FALSE.equals(value);
    }

    public static String typeName(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof Double) {
            return "number";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof LuaTable) {
            return "table";
        }
        if (value instanceof LuaFunction || value instanceof LuaFunction.NativeFunction) {
            return "function";
        }
        if (value instanceof LuaCoroutine) {
            return "thread";
        }
        return "userdata";
    }

    public static double toNumber(Object value, String context) {
        if (value instanceof Double number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                // report below
            }
        }
        throw new LuaError("attempt to " + context + " a " + typeName(value) + " value");
    }

    /** Lua's tostring, including its number formatting (1.0 prints as 1). */
    public static String luaToString(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof Double number) {
            double rounded = Math.floor(number);
            if (number == rounded && !Double.isInfinite(number) && Math.abs(number) < 1e15D) {
                return Long.toString(number.longValue());
            }
            return number.toString();
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Boolean flag) {
            return flag ? "true" : "false";
        }
        return value.toString();
    }

    private static String concatString(Object value) {
        if (value instanceof Double || value instanceof String) {
            return luaToString(value);
        }
        throw new LuaError("attempt to concatenate a " + typeName(value) + " value");
    }

    private static boolean equal(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Double left && b instanceof Double right) {
            return left.doubleValue() == right.doubleValue();
        }
        if (a instanceof String left && b instanceof String right) {
            return left.equals(right);
        }
        if (a instanceof Boolean || b instanceof Boolean) {
            return a.equals(b);
        }
        return a == b;
    }

    private void compare(Frame frame, int mode) {
        Object right = frame.pop();
        Object left = frame.pop();
        int result;
        if (left instanceof Double a && right instanceof Double b) {
            result = Double.compare(a, b);
        } else if (left instanceof String a && right instanceof String b) {
            result = a.compareTo(b);
        } else {
            throw new LuaError("attempt to compare " + typeName(left) + " with " + typeName(right));
        }
        boolean value = switch (mode) {
            case -1 -> result < 0;
            case 0 -> result <= 0;
            case 1 -> result > 0;
            default -> result >= 0;
        };
        frame.push(value);
    }

    private void arithmetic(Frame frame, String operator) {
        Object right = frame.pop();
        Object left = frame.pop();
        double a = toNumber(left, "perform arithmetic on");
        double b = toNumber(right, "perform arithmetic on");
        double result = switch (operator) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            case "%" -> a - Math.floor(a / b) * b;
            case "^" -> Math.pow(a, b);
            default -> throw new LuaError("bad operator " + operator);
        };
        frame.push(result);
    }

    private Object index(Object table, Object key) {
        if (table instanceof LuaTable luaTable) {
            return luaTable.get(key);
        }
        if (table instanceof String && key instanceof String) {
            // Strings answer the string library, so ('x'):upper() works without metatables.
            Object library = globals.get("string");
            if (library instanceof LuaTable stringLib) {
                return stringLib.get(key);
            }
        }
        if (table == null) {
            throw new LuaError("attempt to index a nil value");
        }
        throw new LuaError("attempt to index a " + typeName(table) + " value");
    }

    private void setIndex(Object table, Object key, Object value) {
        if (table instanceof LuaTable luaTable) {
            luaTable.set(key, value);
            return;
        }
        if (table == null) {
            throw new LuaError("attempt to index a nil value");
        }
        throw new LuaError("attempt to index a " + typeName(table) + " value");
    }
}
