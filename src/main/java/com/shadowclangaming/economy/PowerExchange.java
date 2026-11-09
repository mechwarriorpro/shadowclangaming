package com.shadowclangaming.economy;

/**
 * The power-to-money curve used by the Auto-Trader.
 *
 * YOUR SPEC: "100 FE/t = $1, however the amount you need to pump in should increase by the cube of
 * the current input."
 *
 * So the n-th currency unit costs 100 x n^3 FE:
 *
 *     1st $1        100 FE
 *     2nd $1        800 FE
 *     3rd $1      2,700 FE
 *    10th $1    100,000 FE
 *    21st $1  9,261,000 FE
 *
 * The total for N units telescopes (sum of k^3 = (N(N+1)/2)^2):
 *
 *     total(N) = 100 x ( N(N+1)/2 )^2
 *
 *     $10   ->        302,500 FE
 *     $100  ->      2,550,250,000 FE
 *     $256  ->  1,079,526,400,000 FE   (one Sun's worth of radius)
 *
 * WHY A CUBE AND NOT A LINE. A linear rate means a big enough reactor prints money, and in a pack
 * with Mekanism, Extreme Reactors and Draconic Evolution somebody always has a big enough reactor.
 * A cube means the machine is a genuinely useful early cash trick that quietly stops being worth
 * feeding - the exact opposite of an infinite money glitch. It also cannot be beaten by placing more
 * Auto-Traders, because the counter is stored per PLAYER in {@link ExchangeLedger}, not per block.
 *
 * The half-second throughput limit (one unit per tick) is what makes "100 FE/t = $1" literally true
 * at the first unit: the machine can never pay more than one unit's worth of FE per tick, so at
 * n = 1 the draw is exactly 100 FE/t.
 */
public final class PowerExchange {

    /** FE for the first unit, and the coefficient of the cube. */
    public static final long BASE_FE_PER_UNIT = 100L;

    /** Marginal cost grows with the cube of the unit index. */
    public static final int EXPONENT = 3;

    /**
     * @param unitsAlreadyCredited how many units this player has ever converted from power
     * @return FE required to earn the next single unit
     */
    public static long feForNextUnit(long unitsAlreadyCredited) {
        long n = unitsAlreadyCredited + 1L;
        if (n <= 0L) {
            return Long.MAX_VALUE; // counter overflowed - stop, do not wrap into cheap money
        }
        try {
            long cube = Math.multiplyExact(Math.multiplyExact(n, n), n);
            return Math.multiplyExact(cube, BASE_FE_PER_UNIT);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** Total FE needed to have earned {@code units} units from scratch. Used by docs and commands. */
    public static long totalFeFor(long units) {
        if (units <= 0L) {
            return 0L;
        }
        try {
            long triangular = Math.multiplyExact(units, units + 1L) / 2L;
            return Math.multiplyExact(Math.multiplyExact(triangular, triangular), BASE_FE_PER_UNIT);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * How many units a player could earn from a given amount of FE, starting from zero.
     * Inverts {@link #totalFeFor(long)} by walking the curve - only used for display, so plain
     * iteration is fine and cannot drift.
     */
    public static long unitsForTotalFe(long fe) {
        long units = 0L;
        long remaining = fe;
        while (true) {
            long cost = feForNextUnit(units);
            if (cost == Long.MAX_VALUE || cost > remaining) {
                return units;
            }
            remaining -= cost;
            units++;
        }
    }

    private PowerExchange() {
    }
}
