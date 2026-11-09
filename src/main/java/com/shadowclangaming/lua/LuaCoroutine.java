package com.shadowclangaming.lua;

/**
 * A Lua thread.
 *
 * Holds its own {@link Vm} (sharing the machine's globals), the function it runs and whether it is alive.
 * {@code resume} runs the paused VM for a slice; {@code yield} hands values out and pauses it. The same
 * mechanism is what lets a computer wait for a redstone pulse without holding the server tick.
 *
 * Note the practical consequence for script authors, which is documented in docs/LUA.md: a yield resumes
 * exactly where it left off, so state lives in locals rather than in the event queue.
 */
public final class LuaCoroutine {

    private final Vm vm;
    private final LuaFunction function;
    private boolean started;
    private boolean dead;

    private LuaCoroutine(Vm parent, LuaFunction function, int instructionBudget) {
        this.vm = new Vm();
        this.vm.globals = parent.globals;
        this.vm.out = parent.out;
        this.function = function;
        this.budget = instructionBudget;
    }

    public static LuaCoroutine create(Vm parent, LuaFunction function) {
        return new LuaCoroutine(parent, function, 100_000);
    }

    private final int budget;

    public boolean dead() {
        return dead;
    }

    public boolean started() {
        return started;
    }

    public Vm vm() {
        return vm;
    }

    /**
     * Resume with values.
     *
     * @return whatever the coroutine yielded or returned
     * @throws LuaError if the coroutine's body raised an error
     */
    public Object[] resume(Object... args) {
        if (dead) {
            throw new LuaError("cannot resume dead coroutine");
        }
        try {
            if (!started) {
                started = true;
                vm.pushEntry(function, args == null ? new Object[0] : args);
            } else {
                vm.resumeWith(args == null ? new Object[0] : args);
            }
            Vm.Status status = vm.run(budget);
            if (status == Vm.Status.DONE) {
                dead = true;
                return vm.lastResults();
            }
            // Paused without yielding: the machine ran out of its slice and will be resumed by the ticker.
            return new Object[0];
        } catch (Vm.Yield yield) {
            Object[] values = yield.values == null ? new Object[0] : yield.values;
            return values;
        } catch (RuntimeException error) {
            // A failed coroutine is dead: its frames are worthless now.
            vm.reset();
            dead = true;
            throw error;
        }
    }

    /** True when the coroutine is paused (as opposed to never started or finished). */
    public boolean suspended() {
        return started && !dead && vm.paused();
    }
}
