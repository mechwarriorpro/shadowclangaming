package com.shadowclangaming.radar;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a track list out of somebody else's object graph, without knowing their types.
 *
 * WHY THIS IS ITS OWN CLASS. The first version of the Create: Radars bridge tried six exact method
 * names and reported "track list not readable on this build" when none of them existed - which is
 * what happened, because their 0.4.x line renamed everything. Renaming is normal for a mod we cannot
 * compile against, so the fix is to stop guessing names and start reading shapes:
 *
 *   - every no-arg method is a candidate if its name looks like a track list,
 *   - so is every field, public or private,
 *   - a Collection, Map or array is walked element by element,
 *   - an element is a position if it exposes x()/y()/z(), getX()/getY()/getZ(), or a pos()-like
 *     accessor that leads to one.
 *
 * Deliberately free of Minecraft imports: it takes Object and returns double[3], which is what lets
 * the logic harness test it with a stub dish instead of a running game.
 */
public final class TrackScanner {

    /** Name fragments that mark something worth reading. Lower-case, matched as substrings. */
    private static final String[] HINTS = {
            "track", "target", "contact", "detect", "scan", "bearing", "entit", "position", "pos", "result"
    };

    /** Accessors that usually lead from a wrapper to the thing it wraps. */
    private static final String[] WRAPPER_ACCESSORS = {"pos", "position", "getPos", "getBlockPos", "getTrackedPos"};

    private TrackScanner() {
    }

    /** Does this name look like it holds tracks? */
    public static boolean looksLikeTracks(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every position we can find on {@code source}.
     *
     * @return x/y/z triples, never null, possibly empty
     */
    public static List<double[]> scan(Object source) {
        List<double[]> points = new ArrayList<>();
        if (source == null) {
            return points;
        }
        scanMethods(source, points);
        if (points.isEmpty()) {
            scanFields(source, points);
        }
        return points;
    }

    /** Reads everything at once, without the early exit, for diagnostics. */
    public static List<double[]> scanAll(Object source) {
        List<double[]> points = new ArrayList<>();
        if (source != null) {
            scanMethods(source, points);
            scanFields(source, points);
        }
        return points;
    }

    /** The no-arg, non-primitive, track-looking accessors a class declares. Used by /term radar. */
    public static List<String> accessors(Class<?> type) {
        List<String> found = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getParameterCount() == 0
                        && !method.getReturnType().isPrimitive()
                        && looksLikeTracks(method.getName())) {
                    found.add(method.getName() + "()");
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ sources

    private static void scanMethods(Object source, List<double[]> points) {
        for (Class<?> cursor = source.getClass(); cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || !looksLikeTracks(method.getName())) {
                    continue;
                }
                Class<?> type = method.getReturnType();
                if (type.isPrimitive() || type == void.class) {
                    continue;
                }
                Object value;
                try {
                    method.setAccessible(true);
                    value = method.invoke(source);
                } catch (Throwable ignored) {
                    // Their internals changed shape. Never throw: we are a guest here.
                    continue;
                }
                collect(value, points, 0);
            }
        }
    }

    private static void scanFields(Object source, List<double[]> points) {
        for (Class<?> cursor = source.getClass(); cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (field.getType().isPrimitive() || !looksLikeTracks(field.getName())) {
                    continue;
                }
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(source);
                } catch (Throwable ignored) {
                    continue;
                }
                collect(value, points, 0);
            }
        }
    }

    // ------------------------------------------------------------------ values

    /**
     * Turn one value into zero or more positions.
     *
     * @param depth guards against a wrapper whose pos() returns itself, or two wrappers pointing at
     *              each other; two levels is more than enough for a tracked entity
     */
    public static void collect(Object value, List<double[]> points, int depth) {
        if (value == null || depth > 3) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                collect(element, points, depth + 1);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) {
                collect(element, points, depth + 1);
            }
            return;
        }
        if (value.getClass().isArray()) {
            if (value instanceof double[] doubles) {
                addTriple(doubles, 0, points);
                return;
            }
            if (value instanceof float[] floats) {
                if (floats.length >= 3) {
                    points.add(new double[]{floats[0], floats[1], floats[2]});
                }
                return;
            }
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                collect(Array.get(value, i), points, depth + 1);
            }
            return;
        }
        // A bare position: x()/y()/z() or getX()/getY()/getZ().
        double[] triple = tripleOf(value);
        if (triple != null) {
            points.add(triple);
            return;
        }
        // A wrapper around a position.
        for (String accessor : WRAPPER_ACCESSORS) {
            Method method = findNoArg(value.getClass(), accessor);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object inner = method.invoke(value);
                if (inner != null && inner != value) {
                    collect(inner, points, depth + 1);
                    if (!points.isEmpty()) {
                        return;
                    }
                }
            } catch (Throwable ignored) {
                // keep trying the next accessor
            }
        }
    }

    /** {@code {x, y, z}} from anything position-shaped, else null. */
    public static double[] tripleOf(Object value) {
        if (value instanceof double[] doubles) {
            return doubles.length >= 3 ? new double[]{doubles[0], doubles[1], doubles[2]} : null;
        }
        Number x = number(value, "x", "getX");
        Number y = number(value, "y", "getY");
        Number z = number(value, "z", "getZ");
        if (x == null || y == null || z == null) {
            return null;
        }
        return new double[]{x.doubleValue(), y.doubleValue(), z.doubleValue()};
    }

    private static Number number(Object value, String first, String second) {
        for (String name : new String[]{first, second}) {
            Method method = findNoArg(value.getClass(), name);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(value);
                if (result instanceof Number number) {
                    return number;
                }
            } catch (Throwable ignored) {
                // fall through to the second name
            }
        }
        return null;
    }

    private static void addTriple(double[] doubles, int offset, List<double[]> points) {
        if (doubles.length >= offset + 3) {
            points.add(new double[]{doubles[offset], doubles[offset + 1], doubles[offset + 2]});
        }
    }

    /** A no-argument method by name anywhere up the hierarchy, or null. */
    public static Method findNoArg(Class<?> type, String name) {
        for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                // keep walking
            }
        }
        return null;
    }
}
