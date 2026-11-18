package com.shadowclangaming.computer;

/**
 * What a channel number may be, and what "no channel" looks like.
 *
 * WHY THIS EXISTS. Every frequency in the mod used to be a bare {@code int} with {@code -1} meaning
 * "unset". That works right up until somebody tunes a pad to channel -193 and the designator then refuses
 * to fire because -193 is "less than zero" and therefore "not tuned" - the exact bug that made the
 * designator say it was not tuned to a channel the player had just tuned it to.
 *
 * So: "unset" is a sentinel that is nowhere near a real channel ({@link #NONE}), channels themselves are
 * SIGNED and may be negative, and every number that enters the system goes through {@link #sanitise} once.
 *
 * The range is deliberately wide (a million either way) and deliberately bounded: a channel is compared and
 * saved as an int everywhere, and an unbounded value is a way to fill the network registry with garbage by
 * accident. Nothing in the mod needs more than a million channels.
 *
 * Pure Java, no Minecraft imports - the harness proves the negatives round-trip.
 */
public final class Channels {

    /** The "this thing has never been tuned" value. Deliberately not a plausible channel. */
    public static final int NONE = Integer.MIN_VALUE;

    public static final int MIN = -999_999;
    public static final int MAX = 999_999;

    private Channels() {
    }

    /** Clamp anything a player (or a chip, or a command) hands us into the supported range. */
    public static int sanitise(int value) {
        if (value == NONE) {
            return NONE;
        }
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** Is this a real channel (as opposed to {@link #NONE})? Negative channels are real. */
    public static boolean isSet(int value) {
        return value != NONE;
    }

    /** For chat, tooltips and status lines. */
    public static String describe(int value) {
        return isSet(value) ? Integer.toString(value) : "unset";
    }

    /**
     * Parse a channel typed by a player.
     *
     * @return the channel, or {@link #NONE} when the text is not a number at all
     */
    public static int parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        try {
            return sanitise(Integer.parseInt(text.trim()));
        } catch (NumberFormatException error) {
            return NONE;
        }
    }

    /** Step a channel, wrapping at the ends of the range - used by the Frequency Chip. */
    public static int step(int value, int by) {
        int current = isSet(value) ? value : 0;
        long next = (long) current + by;
        if (next > MAX) {
            next = MIN;
        }
        if (next < MIN) {
            next = MAX;
        }
        return (int) next;
    }
}
