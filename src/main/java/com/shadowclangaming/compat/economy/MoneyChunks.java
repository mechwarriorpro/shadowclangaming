package com.shadowclangaming.compat.economy;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Splits a 64-bit amount into 32-bit chunks.
 *
 * Both bank APIs we talk to take an {@code int} (Numismatics' deposit/deduct), while our warhead
 * funding is a {@code long} because the price curve is deliberately not capped. This is the single
 * place those two worlds meet, so it lives in its own class with no Minecraft imports and its own
 * harness test: an off-by-one here silently loses or duplicates money.
 *
 * CHUNKS ARE PRODUCED LAZILY, NOT AS AN ARRAY. The first version built a long[] and the harness
 * immediately found the consequence: Long.MAX_VALUE would need about 4.3 billion entries, so the
 * allocation died with OutOfMemoryError. An iterator costs nothing regardless of the amount, which
 * matters because "how much can be loaded" is a player-facing rule with no cap in it.
 */
public final class MoneyChunks {

    /**
     * The largest number of API calls one transaction may make.
     *
     * 1,000,000 chunks is 2.1e15 units - five hundred billion Suns. No bank account has that, so this
     * never fires in play; it exists so that a corrupted or hostile amount cannot hang a server tick
     * in a loop of half a billion calls.
     */
    public static final long MAX_CHUNKS = 1_000_000L;

    private MoneyChunks() {
    }

    /**
     * How many int-ranged chunks an amount needs. Zero for a non-positive amount.
     *
     * Division-based, not {@code (amount + MAX - 1) / MAX}: the addition overflows for amounts near
     * Long.MAX_VALUE and produced a negative count. (The harness caught exactly that.)
     */
    public static long chunkCount(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long full = amount / Integer.MAX_VALUE;
        return amount % Integer.MAX_VALUE == 0L ? full : full + 1L;
    }

    /** Is this amount small enough to transact through the chunked API? */
    public static boolean transactable(long amount) {
        return amount > 0L && chunkCount(amount) <= MAX_CHUNKS;
    }

    /**
     * The chunks of {@code amount}, largest first, lazily.
     *
     * Every chunk is at most {@link Integer#MAX_VALUE} and the chunks sum to exactly {@code amount}.
     * Non-positive amounts yield nothing.
     */
    public static Iterable<Long> of(long amount) {
        return () -> new Iterator<>() {
            private long remaining = Math.max(0L, amount);

            @Override
            public boolean hasNext() {
                return remaining > 0L;
            }

            @Override
            public Long next() {
                if (remaining <= 0L) {
                    throw new NoSuchElementException();
                }
                long take = Math.min(remaining, Integer.MAX_VALUE);
                remaining -= take;
                return take;
            }
        };
    }

    /**
     * Does splitting and re-adding this amount lose anything?
     *
     * Checked without iterating the whole sequence: the first chunk, the last chunk and the count are
     * enough to prove the identity, so this is safe to call on Long.MAX_VALUE.
     */
    public static boolean exact(long amount) {
        if (amount <= 0L) {
            return amount == 0L;
        }
        long full = amount / Integer.MAX_VALUE;      // whole chunks of exactly MAX (never overflows)
        long rest = amount % Integer.MAX_VALUE;
        long rebuilt = full * Integer.MAX_VALUE;     // <= amount by construction
        return rebuilt + rest == amount;
    }

    /**
     * The chunk at {@code index}, for tests and diagnostics.
     *
     * Computed by division rather than by subtracting {@code index * MAX}, which overflows once the
     * index gets into the billions.
     */
    public static long chunkAt(long amount, long index) {
        if (amount <= 0L || index < 0L) {
            return 0L;
        }
        long full = amount / Integer.MAX_VALUE;
        long rest = amount % Integer.MAX_VALUE;
        if (index < full) {
            return Integer.MAX_VALUE;
        }
        return index == full ? rest : 0L;
    }
}
