package com.shadowclangaming.lua;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Lua table: the only data structure in the language.
 *
 * Numbers are always {@link Double}, strings are {@link String}, so key equality just works if numbers are
 * normalised once on the way in. There is no array/hash split - a table is one insertion-ordered map - and
 * {@link #length()} is the sequence length (count of keys 1..n with no holes), which is what {@code #}
 * means for the tables programs actually build.
 */
public final class LuaTable {

    private final Map<Object, Object> map = new LinkedHashMap<>();

    public Object get(Object key) {
        Object normalised = normalise(key);
        if (normalised == null) {
            return null;
        }
        return map.get(normalised);
    }

    public void set(Object key, Object value) {
        Object normalised = normalise(key);
        if (normalised == null) {
            throw new LuaError("table index is nil");
        }
        if (value == null) {
            map.remove(normalised);
        } else {
            map.put(normalised, value);
        }
    }

    public boolean has(Object key) {
        Object normalised = normalise(key);
        return normalised != null && map.containsKey(normalised);
    }

    public int size() {
        return map.size();
    }

    /** Sequence length: how many 1, 2, 3 ... keys exist before the first hole. */
    public int length() {
        int count = 0;
        while (map.containsKey((double) (count + 1))) {
            count++;
        }
        return count;
    }

    public List<Object> keys() {
        return new ArrayList<>(map.keySet());
    }

    public Map<Object, Object> raw() {
        return map;
    }

    /** Build from a Java list, 1-based, as the table constructor does. */
    public void setList(List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            set((double) (i + 1), values.get(i));
        }
    }

    private static Object normalise(Object key) {
        if (key == null) {
            return null;
        }
        if (key instanceof Double number) {
            if (number.isNaN()) {
                throw new LuaError("table index is NaN");
            }
            return number;
        }
        if (key instanceof Integer || key instanceof Long || key instanceof Float) {
            return ((Number) key).doubleValue();
        }
        if (key instanceof LuaTable || key instanceof LuaFunction || key instanceof LuaFunction.NativeFunction) {
            return key; // identity keys are legal in Lua
        }
        return key;
    }

    @Override
    public String toString() {
        return "table: " + Integer.toHexString(System.identityHashCode(this));
    }
}
