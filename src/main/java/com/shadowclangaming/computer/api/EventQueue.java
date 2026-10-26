package com.shadowclangaming.computer.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The event queue.
 *
 * Everything asynchronous in a computer ends up here: redstone changes, keyboard input, a modem message,
 * a timer firing, a thread finishing. {@code event.pull} takes from the front and suspends the script when
 * the queue is empty, which is the whole reason the Lua machine can pause mid-script.
 *
 * The queue is bounded. A machine whose script never reads its events would otherwise grow until the
 * server runs out of memory, and a computer is not important enough to do that.
 */
public final class EventQueue {

    /** An event: a name plus values, exactly what the script receives. */
    public record Event(String name, Object[] args) {
        public Event {
            args = args == null ? new Object[0] : args;
        }
    }

    public static final int CAPACITY = 256;

    private final Deque<Event> queue = new ArrayDeque<>();

    public void push(String name, Object... args) {
        if (queue.size() >= CAPACITY) {
            queue.removeFirst();
        }
        queue.addLast(new Event(name, args));
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    /** First event that matches (any name when {@code name} is null), without removing it. */
    public Event peek(String name) {
        for (Event event : queue) {
            if (name == null || name.equals(event.name())) {
                return event;
            }
        }
        return null;
    }

    /** Remove the first matching event, or null when there is none. */
    public Event take(String name) {
        Event found = peek(name);
        if (found != null) {
            queue.remove(found);
        }
        return found;
    }

    /** How many events are waiting for this name (0 for "any"). */
    public int count(String name) {
        if (name == null) {
            return queue.size();
        }
        int total = 0;
        for (Event event : queue) {
            if (name.equals(event.name())) {
                total++;
            }
        }
        return total;
    }

    public List<Event> drain() {
        List<Event> events = new ArrayList<>(queue);
        queue.clear();
        return events;
    }

    public void clear() {
        queue.clear();
    }

    /** Names currently queued, for {@code computer.pushSignal} diagnostics and the panel. */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Event event : queue) {
            names.add(event.name());
        }
        return names;
    }
}
