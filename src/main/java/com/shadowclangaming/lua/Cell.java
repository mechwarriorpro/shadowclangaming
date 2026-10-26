package com.shadowclangaming.lua;

/**
 * A box holding one local or upvalue.
 *
 * Every local is boxed, even ones no closure ever touches. That costs an allocation per local and buys
 * something much more valuable: upvalue capture becomes trivial and always correct, because a closure
 * simply keeps the same box the enclosing function writes to. This interpreter runs under an instruction
 * budget on a server tick, not in a benchmark, so clarity wins.
 */
public final class Cell {

    public Object value;

    public Cell(Object value) {
        this.value = value;
    }
}
