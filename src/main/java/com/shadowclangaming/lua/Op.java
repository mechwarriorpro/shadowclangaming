package com.shadowclangaming.lua;

/**
 * The instruction set.
 *
 * A stack machine, because a stack machine is the only shape that makes both of the things this mod needs
 * easy at the same time:
 *
 *   - **suspension.** A script must give the server its tick back when it waits for an event or exhausts
 *     its instruction budget. With an explicit frame (pc + slots + value stack) that is just "return from
 *     the loop"; a tree-walking interpreter could not do it at all without threads.
 *   - **coroutines.** {@code coroutine.create/resume/yield} become the same save/restore, reused.
 *
 * Instructions are {@code int[]} with the opcode in slot 0; jumps carry their target in slot 1.
 */
public final class Op {

    // pushing values
    public static final int PUSH_CONST = 1;   // a = constant index
    public static final int PUSH_NIL = 2;
    public static final int PUSH_TRUE = 3;
    public static final int PUSH_FALSE = 4;
    public static final int DUP = 5;
    public static final int POP = 6;

    // variables
    public static final int LOAD_LOCAL = 7;   // a = slot
    public static final int STORE_LOCAL = 8;  // a = slot (pops)
    public static final int LOAD_UPVAL = 9;   // a = upvalue
    public static final int STORE_UPVAL = 10;
    public static final int LOAD_GLOBAL = 11; // a = constant name
    public static final int STORE_GLOBAL = 12;
    public static final int VARARG = 13;      // a = how many results to push (pads with nil)
    public static final int VARARG_ALL = 14;  // push all varargs, sets the multi-value count

    // tables
    public static final int NEW_TABLE = 15;   // a = array count popped from the stack
    public static final int GET_INDEX = 16;   // t k -> v
    public static final int SET_INDEX = 17;   // t k v ->
    public static final int GET_FIELD = 18;   // a = name constant; t -> t[a]
    public static final int SET_FIELD = 19;   // a = name constant; t v ->

    // arithmetic and comparison
    public static final int ADD = 20;
    public static final int SUB = 21;
    public static final int MUL = 22;
    public static final int DIV = 23;
    public static final int MOD = 24;
    public static final int POW = 25;
    public static final int CONCAT = 26;      // a = count
    public static final int NEG = 27;
    public static final int NOT = 28;
    public static final int LEN = 29;
    public static final int EQ = 30;
    public static final int NE = 31;
    public static final int LT = 32;
    public static final int LE = 33;
    public static final int GT = 34;
    public static final int GE = 35;

    // branching
    public static final int JMP = 36;             // a = target
    public static final int JMP_FALSE = 37;       // a = target, pops
    public static final int JMP_FALSE_KEEP = 38;  // a = target, leaves the value (and)
    public static final int JMP_TRUE_KEEP = 39;   // a = target, leaves the value (or)

    // calls
    public static final int CALL = 40;            // a = args; one result
    public static final int CALL_MULTI = 41;      // a = args, b = wanted results
    public static final int CALL_ALL = 42;        // a = args; all results, sets multi count
    public static final int METHOD_CALL = 43;     // a = args, b = name constant
    public static final int METHOD_CALL_MULTI = 44; // a = args, b = name, c = wanted
    public static final int RETURN = 45;          // a = values
    public static final int RETURN_NONE = 46;
    public static final int RETURN_MULTI = 47;    // returns the multi count from CALL_ALL/VARARG_ALL
    public static final int MAKE_CLOSURE = 48;    // a = closure spec index

    public static final int CALL_TAIL = 53;        // a = fixed args; last argument expanded (a + multi args)
    public static final int METHOD_CALL_TAIL = 54; // a = fixed args, b = name constant; last expanded
    public static final int STORE_SLOTS = 55;      // a = base slot, b = count: takes the multi values
    public static final int APPEND_MULTI = 56;     // a = first array index: t -> t, appends the multi values

    // loops
    public static final int FOR_NUM_INIT = 49;    // a = base, b = end target
    public static final int FOR_NUM_NEXT = 50;    // a = base, b = loop target
    public static final int FOR_GEN_INIT = 51;    // a = base, b = end target, c = result count
    public static final int FOR_GEN_NEXT = 52;    // a = base, b = loop target, c = result count

    private Op() {
    }
}
