package com.shadowclangaming.lua;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The standard library, minus anything that would let a script reach outside the computer.
 *
 * Deliberately absent: {@code io}, {@code os.execute}, {@code require} of Java classes, {@code load} of
 * arbitrary strings (a datapack could otherwise inject code into a machine), and metatables. What is here
 * is the set a real script needs: strings, tables, maths, and the error handling primitives.
 *
 * Number formatting follows Lua (1.0 prints as "1") and {@code string.format} supports the directives that
 * appear in real code (%s %d %i %f %g %x %X %c %%), including width and precision.
 */
public final class Stdlib {

    private static final Random RANDOM = new Random();

    private Stdlib() {
    }

    public static void install(Vm vm) {
        LuaTable g = vm.globals;

        g.set("_VERSION", "Lua 5.1 (ShadowClanGaming subset)");
        native_(g, "print", (machine, args) -> {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    line.append('\t');
                }
                line.append(Vm.luaToString(args[i]));
            }
            machine.out.accept(line.toString());
            return NOTHING;
        });
        native_(g, "type", (machine, args) -> one(Vm.typeName(arg(args, 0))));
        native_(g, "tostring", (machine, args) -> one(Vm.luaToString(arg(args, 0))));
        native_(g, "tonumber", (machine, args) -> {
            Object value = arg(args, 0);
            if (value == null) {
                return one(null);
            }
            if (value instanceof Double) {
                return one(value);
            }
            String text = Vm.luaToString(value).trim();
            try {
                if (args.length > 1) {
                    int base = (int) Vm.toNumber(arg(args, 1), "convert base");
                    return one((double) Long.parseLong(text, base));
                }
                return one(Double.parseDouble(text));
            } catch (NumberFormatException error) {
                return one(null);
            }
        });
        native_(g, "error", (machine, args) -> {
            Object message = arg(args, 0);
            throw new LuaError(Vm.luaToString(message));
        });
        native_(g, "assert", (machine, args) -> {
            if (!Vm.truthy(arg(args, 0))) {
                throw new LuaError(args.length > 1 ? Vm.luaToString(args[1]) : "assertion failed!");
            }
            return args;
        });
        native_(g, "pcall", (machine, args) -> {
            Object function = arg(args, 0);
            Object[] rest = new Object[Math.max(0, args.length - 1)];
            System.arraycopy(args, 1, rest, 0, rest.length);
            int depth = machine.depth();
            try {
                Object[] results = machine.callNow(function, rest);
                Object[] out = new Object[results.length + 1];
                out[0] = Boolean.TRUE;
                System.arraycopy(results, 0, out, 1, results.length);
                return out;
            } catch (LuaError error) {
                machine.unwindTo(depth);
                return new Object[]{Boolean.FALSE, error.getMessage()};
            } catch (RuntimeException error) {
                machine.unwindTo(depth);
                return new Object[]{Boolean.FALSE, String.valueOf(error)};
            }
        });
        native_(g, "rawget", (machine, args) -> one(table(args, 0).get(arg(args, 1))));
        native_(g, "rawset", (machine, args) -> {
            LuaTable target = table(args, 0);
            target.set(arg(args, 1), arg(args, 2));
            return one(target);
        });
        native_(g, "rawequal", (machine, args) -> one(arg(args, 0) == arg(args, 1)));
        native_(g, "next", (machine, args) -> next(args, 0));
        native_(g, "pairs", (machine, args) -> {
            LuaTable target = table(args, 0);
            return new Object[]{machine.globals.get("next"), target, null};
        });
        native_(g, "ipairs", (machine, args) -> {
            LuaTable target = table(args, 0);
            LuaFunction.NativeFunction iterator = new LuaFunction.NativeFunction("ipairs_iter",
                    (machine1, iteratorArgs) -> {
                        LuaTable t = table(iteratorArgs, 0);
                        double index = Vm.toNumber(arg(iteratorArgs, 1), "convert index") + 1.0D;
                        Object value = t.get(index);
                        if (value == null) {
                            return NOTHING;
                        }
                        return new Object[]{index, value};
                    });
            return new Object[]{iterator, target, 0.0D};
        });
        native_(g, "select", (machine, args) -> {
            Object first = arg(args, 0);
            if ("#".equals(first)) {
                return one((double) Math.max(0, args.length - 1));
            }
            int index = (int) Vm.toNumber(first, "convert index");
            if (index < 0) {
                index = args.length + index;
            }
            List<Object> out = new ArrayList<>();
            for (int i = index; i < args.length; i++) {
                out.add(args[i]);
            }
            return out.toArray();
        });
        native_(g, "setmetatable", (machine, args) -> {
            throw new LuaError("metatables are not supported by this Lua subset");
        });
        native_(g, "getmetatable", (machine, args) -> one(null));

        stringLibrary(vm, g);
        tableLibrary(vm, g);
        mathLibrary(vm, g);
        coroutineLibrary(vm, g);
    }

    // ------------------------------------------------------------------ libraries

    private static void stringLibrary(Vm vm, LuaTable g) {
        LuaTable string = new LuaTable();
        g.set("string", string);
        native_(string, "len", (machine, args) -> one((double) text(args, 0).length()));
        native_(string, "sub", (machine, args) -> {
            String value = text(args, 0);
            int length = value.length();
            int from = (int) Vm.toNumber(arg(args, 1), "convert index");
            int to = args.length > 2 ? (int) Vm.toNumber(arg(args, 2), "convert index") : -1;
            if (from < 0) {
                from = Math.max(1, length + from + 1);
            }
            if (from == 0) {
                from = 1;
            }
            if (to < 0) {
                to = length + to + 1;
            }
            to = Math.min(to, length);
            if (from > to) {
                return one("");
            }
            return one(value.substring(from - 1, to));
        });
        native_(string, "upper", (machine, args) -> one(text(args, 0).toUpperCase(java.util.Locale.ROOT)));
        native_(string, "lower", (machine, args) -> one(text(args, 0).toLowerCase(java.util.Locale.ROOT)));
        native_(string, "rep", (machine, args) -> {
            String value = text(args, 0);
            int count = (int) Vm.toNumber(arg(args, 1), "convert count");
            if (count <= 0) {
                return one("");
            }
            if ((long) value.length() * count > 1_000_000L) {
                throw new LuaError("string.rep result too large");
            }
            return one(value.repeat(count));
        });
        native_(string, "reverse", (machine, args) -> one(new StringBuilder(text(args, 0)).reverse().toString()));
        native_(string, "byte", (machine, args) -> {
            String value = text(args, 0);
            int at = args.length > 1 ? (int) Vm.toNumber(arg(args, 1), "convert index") : 1;
            if (at < 1 || at > value.length()) {
                return NOTHING;
            }
            return one((double) value.charAt(at - 1));
        });
        native_(string, "char", (machine, args) -> {
            StringBuilder builder = new StringBuilder();
            for (Object argument : args) {
                builder.append((char) (int) Vm.toNumber(argument, "convert character code"));
            }
            return one(builder.toString());
        });
        native_(string, "format", (machine, args) -> one(format(args)));
        native_(string, "find", (machine, args) -> {
            // Plain finding only: patterns are not supported (docs/LUA.md).
            String haystack = text(args, 0);
            String needle = text(args, 1);
            int from = args.length > 2 ? (int) Vm.toNumber(arg(args, 2), "convert index") : 1;
            if (from < 0) {
                from = Math.max(1, haystack.length() + from + 1);
            }
            int index = haystack.indexOf(needle, Math.max(0, from - 1));
            if (index < 0) {
                return one(null);
            }
            return new Object[]{(double) (index + 1), (double) (index + needle.length())};
        });
        native_(string, "gsub", (machine, args) -> {
            // Literal replacement only, no patterns and no function replacements.
            String haystack = text(args, 0);
            String needle = text(args, 1);
            String replacement = Vm.luaToString(arg(args, 2));
            if (needle.isEmpty()) {
                return new Object[]{haystack, 0.0D};
            }
            int count = 0;
            StringBuilder builder = new StringBuilder();
            int cursor = 0;
            while (true) {
                int index = haystack.indexOf(needle, cursor);
                if (index < 0) {
                    builder.append(haystack, cursor, haystack.length());
                    break;
                }
                builder.append(haystack, cursor, index).append(replacement);
                cursor = index + needle.length();
                count++;
            }
            return new Object[]{builder.toString(), (double) count};
        });
    }

    private static void tableLibrary(Vm vm, LuaTable g) {
        LuaTable table = new LuaTable();
        g.set("table", table);
        native_(table, "insert", (machine, args) -> {
            LuaTable target = table(args, 0);
            if (args.length >= 3) {
                int at = (int) Vm.toNumber(arg(args, 1), "convert index");
                for (int i = target.length(); i >= at; i--) {
                    target.set((double) (i + 1), target.get((double) i));
                }
                target.set((double) at, arg(args, 2));
            } else {
                target.set((double) (target.length() + 1), arg(args, 1));
            }
            return NOTHING;
        });
        native_(table, "remove", (machine, args) -> {
            LuaTable target = table(args, 0);
            int length = target.length();
            int at = args.length > 1 ? (int) Vm.toNumber(arg(args, 1), "convert index") : length;
            if (at < 1 || at > length) {
                return one(null);
            }
            Object removed = target.get((double) at);
            for (int i = at; i < length; i++) {
                target.set((double) i, target.get((double) (i + 1)));
            }
            target.set((double) length, null);
            return one(removed);
        });
        native_(table, "concat", (machine, args) -> {
            LuaTable target = table(args, 0);
            String separator = args.length > 1 ? Vm.luaToString(arg(args, 1)) : "";
            int from = args.length > 2 ? (int) Vm.toNumber(arg(args, 2), "convert index") : 1;
            int to = args.length > 3 ? (int) Vm.toNumber(arg(args, 3), "convert index") : target.length();
            StringBuilder builder = new StringBuilder();
            for (int i = from; i <= to; i++) {
                if (i > from) {
                    builder.append(separator);
                }
                builder.append(Vm.luaToString(target.get((double) i)));
            }
            return one(builder.toString());
        });
        native_(table, "unpack", (machine, args) -> {
            LuaTable target = table(args, 0);
            int from = args.length > 1 ? (int) Vm.toNumber(arg(args, 1), "convert index") : 1;
            int to = args.length > 2 ? (int) Vm.toNumber(arg(args, 2), "convert index") : target.length();
            Object[] out = new Object[Math.max(0, to - from + 1)];
            for (int i = 0; i < out.length; i++) {
                out[i] = target.get((double) (from + i));
            }
            return out;
        });
        g.set("unpack", table.get("unpack"));
        native_(table, "sort", (machine, args) -> {
            LuaTable target = table(args, 0);
            Object comparator = arg(args, 1);
            List<Object> values = new ArrayList<>();
            for (int i = 1; i <= target.length(); i++) {
                values.add(target.get((double) i));
            }
            values.sort((left, right) -> {
                if (comparator != null) {
                    Object[] result = machine.callNow(comparator, new Object[]{left, right});
                    return Vm.truthy(result.length > 0 ? result[0] : null) ? -1 : 1;
                }
                if (left instanceof Double a && right instanceof Double b) {
                    return Double.compare(a, b);
                }
                return Vm.luaToString(left).compareTo(Vm.luaToString(right));
            });
            for (int i = 0; i < values.size(); i++) {
                target.set((double) (i + 1), values.get(i));
            }
            return NOTHING;
        });
    }

    private static void mathLibrary(Vm vm, LuaTable g) {
        LuaTable math = new LuaTable();
        g.set("math", math);
        math.set("pi", Math.PI);
        math.set("huge", Double.POSITIVE_INFINITY);
        unary(math, "floor", Math::floor);
        unary(math, "ceil", Math::ceil);
        unary(math, "abs", Math::abs);
        unary(math, "sqrt", Math::sqrt);
        unary(math, "sin", Math::sin);
        unary(math, "cos", Math::cos);
        unary(math, "tan", Math::tan);
        unary(math, "exp", Math::exp);
        native_(math, "max", (machine, args) -> {
            double best = Double.NEGATIVE_INFINITY;
            for (Object argument : args) {
                best = Math.max(best, Vm.toNumber(argument, "compare"));
            }
            return one(best);
        });
        native_(math, "min", (machine, args) -> {
            double best = Double.POSITIVE_INFINITY;
            for (Object argument : args) {
                best = Math.min(best, Vm.toNumber(argument, "compare"));
            }
            return one(best);
        });
        native_(math, "fmod", (machine, args) -> {
            double a = Vm.toNumber(arg(args, 0), "convert");
            double b = Vm.toNumber(arg(args, 1), "convert");
            return one(a % b);
        });
        native_(math, "random", (machine, args) -> {
            if (args.length == 0) {
                return one(RANDOM.nextDouble());
            }
            int upper = (int) Vm.toNumber(arg(args, 0), "convert bound");
            if (args.length == 1) {
                return one((double) (RANDOM.nextInt(Math.max(1, upper)) + 1));
            }
            int lower = upper;
            upper = (int) Vm.toNumber(arg(args, 1), "convert bound");
            if (upper < lower) {
                int swap = lower;
                lower = upper;
                upper = swap;
            }
            return one((double) (lower + RANDOM.nextInt(Math.max(1, upper - lower + 1))));
        });
        native_(math, "randomseed", (machine, args) -> {
            RANDOM.setSeed((long) Vm.toNumber(arg(args, 0), "convert seed"));
            return NOTHING;
        });
    }

    private static void coroutineLibrary(Vm vm, LuaTable g) {
        LuaTable coroutine = new LuaTable();
        g.set("coroutine", coroutine);
        native_(coroutine, "create", (machine, args) -> {
            Object function = arg(args, 0);
            if (!(function instanceof LuaFunction lua)) {
                throw new LuaError("coroutine.create needs a Lua function");
            }
            return one(LuaCoroutine.create(machine, lua));
        });
        native_(coroutine, "resume", (machine, args) -> {
            if (!(arg(args, 0) instanceof LuaCoroutine thread)) {
                throw new LuaError("coroutine.resume needs a thread");
            }
            Object[] rest = new Object[Math.max(0, args.length - 1)];
            System.arraycopy(args, 1, rest, 0, rest.length);
            try {
                Object[] values = thread.resume(rest);
                Object[] out = new Object[values.length + 1];
                out[0] = Boolean.TRUE;
                System.arraycopy(values, 0, out, 1, values.length);
                return out;
            } catch (LuaError error) {
                return new Object[]{Boolean.FALSE, error.getMessage()};
            }
        });
        native_(coroutine, "yield", (machine, args) -> {
            throw Vm.Yield.of(args);
        });
        native_(coroutine, "status", (machine, args) -> {
            if (!(arg(args, 0) instanceof LuaCoroutine thread)) {
                throw new LuaError("coroutine.status needs a thread");
            }
            if (thread.dead()) {
                return one("dead");
            }
            return one(thread.started() ? "suspended" : "suspended");
        });
        native_(coroutine, "wrap", (machine, args) -> {
            Object function = arg(args, 0);
            if (!(function instanceof LuaFunction lua)) {
                throw new LuaError("coroutine.wrap needs a Lua function");
            }
            LuaCoroutine thread = LuaCoroutine.create(machine, lua);
            return one(new LuaFunction.NativeFunction("wrapped", (machine1, wrapArgs) ->
                    thread.resume(wrapArgs)));
        });
    }

    // ------------------------------------------------------------------ helpers

    private static final Object[] NOTHING = new Object[0];

    private static Object[] one(Object value) {
        return new Object[]{value};
    }

    private static Object arg(Object[] args, int index) {
        return index >= 0 && index < args.length ? args[index] : null;
    }

    private static String text(Object[] args, int index) {
        Object value = arg(args, index);
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Double) {
            return Vm.luaToString(value);
        }
        throw new LuaError("string expected, got " + Vm.typeName(value));
    }

    private static LuaTable table(Object[] args, int index) {
        Object value = arg(args, index);
        if (value instanceof LuaTable luaTable) {
            return luaTable;
        }
        throw new LuaError("table expected, got " + Vm.typeName(value));
    }

    private static Object[] next(Object[] args, int index) {
        LuaTable target = table(args, 0);
        Object key = arg(args, 1);
        List<Object> keys = target.keys();
        if (key == null) {
            return keys.isEmpty() ? one(null) : new Object[]{keys.get(0), target.get(keys.get(0))};
        }
        int position = keys.indexOf(key) + 1;
        if (position <= 0 || position >= keys.size()) {
            return one(null);
        }
        Object found = keys.get(position);
        return new Object[]{found, target.get(found)};
    }

    private static void native_(LuaTable target, String name, LuaFunction.Native body) {
        target.set(name, new LuaFunction.NativeFunction(name, body));
    }

    private static void unary(LuaTable target, String name, java.util.function.DoubleUnaryOperator operator) {
        native_(target, name, (machine, args) ->
                one(operator.applyAsDouble(Vm.toNumber(arg(args, 0), "convert"))));
    }

    /** {@code string.format}: %s %d %i %f %g %x %X %c %% with optional width and precision. */
    static String format(Object[] args) {
        String pattern = text(args, 0);
        StringBuilder out = new StringBuilder();
        int argument = 1;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch != '%') {
                out.append(ch);
                continue;
            }
            i++;
            if (i >= pattern.length()) {
                throw new LuaError("string.format: trailing %");
            }
            StringBuilder spec = new StringBuilder("%");
            while (i < pattern.length() && "-+ 0#123456789.".indexOf(pattern.charAt(i)) >= 0) {
                spec.append(pattern.charAt(i++));
            }
            if (i >= pattern.length()) {
                throw new LuaError("string.format: incomplete directive");
            }
            char conversion = pattern.charAt(i);
            // "%%" is a literal percent sign and takes NO argument. Fetching the value before this check
            // shifted every later argument by one, which is how "100%% done, %d left" printed the wrong
            // number - or threw, if the count ran out. (Found by the barrage example program.)
            if (conversion == '%') {
                out.append('%');
                continue;
            }
            Object value = argument < args.length ? args[argument++] : null;
            switch (conversion) {
                case 's' -> out.append(String.format(java.util.Locale.ROOT, spec.toString() + "s",
                        Vm.luaToString(value)));
                case 'd', 'i' -> out.append(String.format(java.util.Locale.ROOT, spec.toString() + "d",
                        (long) Vm.toNumber(value, "convert number")));
                case 'x' -> out.append(String.format(java.util.Locale.ROOT, spec.toString() + "x",
                        (long) Vm.toNumber(value, "convert number")));
                case 'X' -> out.append(String.format(java.util.Locale.ROOT, spec.toString() + "X",
                        (long) Vm.toNumber(value, "convert number")));
                case 'f', 'g', 'e' -> out.append(String.format(java.util.Locale.ROOT,
                        spec.toString() + conversion, Vm.toNumber(value, "convert number")));
                case 'c' -> out.append((char) (int) Vm.toNumber(value, "convert character code"));
                default -> throw new LuaError("string.format: unsupported directive %" + conversion);
            }
        }
        return out.toString();
    }

    /** A session-scoped random source, exposed so tests can be deterministic. */
    public static void seed(long value) {
        RANDOM.setSeed(value);
    }
}
