package com.shadowclangaming.computer.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The components one machine can reach.
 *
 * Rebuilt on demand by the block entity (components come and go as cables are broken), so nothing here
 * caches across a tick. Lookups are by address, and address lookups may be shortened: {@code component.get}
 * accepts a unique prefix, which is how hand-typed shell commands refer to screens.
 */
public final class ComponentRegistry {

    private final Map<String, Component> byAddress = new LinkedHashMap<>();

    public void clear() {
        byAddress.clear();
    }

    public void register(Component component) {
        byAddress.put(component.address(), component);
    }

    public void unregister(String address) {
        byAddress.remove(address);
    }

    public List<Component> all() {
        return new ArrayList<>(byAddress.values());
    }

    public List<String> addresses() {
        return new ArrayList<>(byAddress.keySet());
    }

    /** Exact address, else a unique prefix; null when missing or ambiguous. */
    public Component get(String address) {
        if (address == null) {
            return null;
        }
        Component exact = byAddress.get(address);
        if (exact != null) {
            return exact;
        }
        Component match = null;
        for (Map.Entry<String, Component> entry : byAddress.entrySet()) {
            if (entry.getKey().startsWith(address)) {
                if (match != null) {
                    return null;
                }
                match = entry.getValue();
            }
        }
        return match;
    }

    /** Components of one type, in registration order. */
    public List<Component> ofType(String type) {
        List<Component> matches = new ArrayList<>();
        for (Component component : byAddress.values()) {
            if (component.type().equals(type)) {
                matches.add(component);
            }
        }
        return matches;
    }
}
