package com.shadowclangaming.lua;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.shadowclangaming.lua.LuaFunction.Proto;

/**
 * Walks the syntax tree and emits bytecode.
 *
 * Two details are worth knowing before reading it:
 *
 * **Slots are never reused.** Scopes pop names, not slot numbers, so a closure that captured a local from
 * an inner block keeps pointing at that local's cell even after a later block declares its own variable.
 * Reusing slots is the classic source of "my closure sees the wrong variable" bugs, and paying a few cells
 * per function is a much better trade than chasing that.
 *
 * **Upvalues are resolved on demand.** When an expression names something that is not local, the emitter
 * walks outwards: an enclosing function's local becomes upvalue descriptor {0, slot}, an enclosing
 * function's upvalue becomes {1, index}. Descriptors are complete by the time MAKE_CLOSURE is emitted,
 * because resolving them happened while emitting the child's body.
 */
public final class Emitter {

    /** One function being compiled. */
    private static final class Fn {
        final Proto proto = new Proto();
        final Fn parent;
        boolean vararg;
        final List<String> locals = new ArrayList<>();
        final List<Integer> localSlots = new ArrayList<>();
        int nextSlot;
        final List<int[]> upvalues = new ArrayList<>();

        Fn(Fn parent) {
            this.parent = parent;
        }
    }

    private Fn fn;
    private final Deque<List<Integer>> breakJumps = new ArrayDeque<>();

    private Emitter() {
    }

    public static Proto compile(String source, String sourceName) {
        Ast.Chunk chunk = Parser.parse(source, sourceName);
        Emitter emitter = new Emitter();
        emitter.fn = new Fn(null);
        emitter.fn.proto.source = sourceName;
        emitter.fn.proto.name = "main";
        emitter.fn.vararg = true;
        emitter.emitBlock(chunk.body());
        emitter.emit(Op.RETURN_NONE, 0, 0, 0);
        emitter.fn.proto.vararg = true;
        emitter.fn.proto.maxSlots = Math.max(1, emitter.fn.nextSlot + 1);
        return emitter.fn.proto;
    }

    // ------------------------------------------------------------------ plumbing

    private int emit(int op, int a, int b, int c) {
        fn.proto.code.add(new int[]{op, a, b, c});
        return fn.proto.code.size() - 1;
    }

    private int here() {
        return fn.proto.code.size();
    }

    private void patch(int index, int target) {
        fn.proto.code.get(index)[1] = target;
    }

    /** Loop instructions carry their jump target in the third word (the second is the loop base). */
    private void patchTarget(int index, int target) {
        fn.proto.code.get(index)[2] = target;
    }

    private int constant(Object value) {
        return fn.proto.constIndex(value);
    }

    private int declare(String name) {
        if (fn.nextSlot >= 200) {
            throw new LuaError("too many locals in one function (200); split it into functions");
        }
        fn.locals.add(name);
        fn.localSlots.add(fn.nextSlot);
        return fn.nextSlot++;
    }

    /** A scratch slot: allocated, but not a named local, so it never leaks into scope or name lookups. */
    private int temp() {
        if (fn.nextSlot >= 250) {
            throw new LuaError("expression too complex (250 scratch slots in one function)");
        }
        return fn.nextSlot++;
    }

    private int mark() {
        return fn.locals.size();
    }

    private void restore(int mark) {
        while (fn.locals.size() > mark) {
            fn.locals.remove(fn.locals.size() - 1);
            fn.localSlots.remove(fn.localSlots.size() - 1);
        }
    }

    private int resolveLocal(String name) {
        for (int i = fn.locals.size() - 1; i >= 0; i--) {
            if (name.equals(fn.locals.get(i))) {
                return fn.localSlots.get(i);
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ statements

    private void emitBlock(List<Ast.Stmt> body) {
        int mark = mark();
        for (Ast.Stmt statement : body) {
            emitStatement(statement);
        }
        restore(mark);
    }

    private void emitStatement(Ast.Stmt statement) {
        if (statement instanceof Ast.LocalAssign local) {
            localAssign(local);
        } else if (statement instanceof Ast.Assign assign) {
            assign(assign);
        } else if (statement instanceof Ast.CallStmt call) {
            emitExpr(call.call());
            emit(Op.POP, 0, 0, 0);
        } else if (statement instanceof Ast.Do block) {
            emitBlock(block.body());
        } else if (statement instanceof Ast.If conditional) {
            ifStatement(conditional);
        } else if (statement instanceof Ast.While loop) {
            whileStatement(loop);
        } else if (statement instanceof Ast.Repeat loop) {
            repeatStatement(loop);
        } else if (statement instanceof Ast.NumericFor loop) {
            numericFor(loop);
        } else if (statement instanceof Ast.GenericFor loop) {
            genericFor(loop);
        } else if (statement instanceof Ast.LocalFunction local) {
            int slot = declare(local.name());
            emitFunction(local.function());
            emit(Op.STORE_LOCAL, slot, 0, 0);
        } else if (statement instanceof Ast.Return statement1) {
            returnStatement(statement1);
        } else if (statement instanceof Ast.Break) {
            if (breakJumps.isEmpty()) {
                throw new LuaError("break outside a loop");
            }
            breakJumps.peek().add(emit(Op.JMP, 0, 0, 0));
        } else {
            throw new LuaError("unsupported statement " + statement.getClass().getSimpleName());
        }
    }

    /** Does this expression produce several values when it is the last one in a list? */
    private static boolean expands(Ast.Expr expression) {
        return expression instanceof Ast.Call || expression instanceof Ast.MethodCall
                || expression instanceof Ast.Vararg;
    }

    /**
     * Evaluate a value list into already-allocated slots, expanding a trailing call or {@code ...} the way
     * Lua does: {@code local a, b = f()} takes two results from f.
     */
    private void emitValuesInto(List<Ast.Expr> values, List<Integer> slots) {
        int count = slots.size();
        for (int i = 0; i < count; i++) {
            Ast.Expr value = i < values.size() ? values.get(i) : null;
            if (value == null) {
                emit(Op.PUSH_NIL, 0, 0, 0);
                emit(Op.STORE_LOCAL, slots.get(i), 0, 0);
                continue;
            }
            if (i == values.size() - 1 && i < count - 1 && expands(value)) {
                if (value instanceof Ast.Vararg) {
                    emit(Op.VARARG_ALL, 0, 0, 0);
                } else {
                    emitCall(value, true);
                }
                emit(Op.STORE_SLOTS, slots.get(i), count - i, 0);
                return;
            }
            emitExpr(value);
            emit(Op.STORE_LOCAL, slots.get(i), 0, 0);
        }
    }

    private void localAssign(Ast.LocalAssign local) {
        List<Integer> temps = new ArrayList<>();
        for (int i = 0; i < local.names().size(); i++) {
            temps.add(temp());
        }
        emitValuesInto(local.values(), temps);
        List<Integer> targets = new ArrayList<>();
        for (String name : local.names()) {
            targets.add(declare(name));
        }
        for (int i = 0; i < targets.size(); i++) {
            emit(Op.LOAD_LOCAL, temps.get(i), 0, 0);
            emit(Op.STORE_LOCAL, targets.get(i), 0, 0);
        }
    }

    private void assign(Ast.Assign assign) {
        int count = assign.targets().size();
        // Snapshot every index target's table and key before any value is evaluated: Lua evaluates the
        // left-hand side first, so `t[i], i = 1, 2` writes to the old i.
        List<Integer> tableTemps = new ArrayList<>();
        List<Integer> keyTemps = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        for (Ast.Expr target : assign.targets()) {
            tableTemps.add(-1);
            keyTemps.add(-1);
            fieldNames.add(null);
            if (target instanceof Ast.Field field) {
                int tableSlot = temp();
                emitExpr(field.table());
                emit(Op.STORE_LOCAL, tableSlot, 0, 0);
                tableTemps.set(tableTemps.size() - 1, tableSlot);
                fieldNames.set(fieldNames.size() - 1, field.name());
            } else if (target instanceof Ast.Index index) {
                int tableSlot = temp();
                emitExpr(index.table());
                emit(Op.STORE_LOCAL, tableSlot, 0, 0);
                int keySlot = temp();
                emitExpr(index.key());
                emit(Op.STORE_LOCAL, keySlot, 0, 0);
                tableTemps.set(tableTemps.size() - 1, tableSlot);
                keyTemps.set(keyTemps.size() - 1, keySlot);
            } else if (!(target instanceof Ast.Name)) {
                throw new LuaError("cannot assign to this expression");
            }
        }

        List<Integer> valueTemps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            valueTemps.add(temp());
        }
        emitValuesInto(assign.values(), valueTemps);

        for (int i = 0; i < count; i++) {
            Ast.Expr target = assign.targets().get(i);
            int value = valueTemps.get(i);
            if (target instanceof Ast.Name name) {
                emit(Op.LOAD_LOCAL, value, 0, 0);
                int local = resolveLocal(name.name());
                if (local >= 0) {
                    emit(Op.STORE_LOCAL, local, 0, 0);
                } else {
                    int upvalue = resolveUpvalue(name.name());
                    if (upvalue >= 0) {
                        emit(Op.STORE_UPVAL, upvalue, 0, 0);
                    } else {
                        emit(Op.STORE_GLOBAL, constant(name.name()), 0, 0);
                    }
                }
            } else if (target instanceof Ast.Field) {
                emit(Op.LOAD_LOCAL, tableTemps.get(i), 0, 0);
                emit(Op.LOAD_LOCAL, value, 0, 0);
                emit(Op.SET_FIELD, constant(fieldNames.get(i)), 0, 0);
            } else {
                emit(Op.LOAD_LOCAL, tableTemps.get(i), 0, 0);
                emit(Op.LOAD_LOCAL, keyTemps.get(i), 0, 0);
                emit(Op.LOAD_LOCAL, value, 0, 0);
                emit(Op.SET_INDEX, 0, 0, 0);
            }
        }
    }

    private void ifStatement(Ast.If conditional) {
        List<Integer> endJumps = new ArrayList<>();
        for (int i = 0; i < conditional.conditions().size(); i++) {
            emitExpr(conditional.conditions().get(i));
            int falseJump = emit(Op.JMP_FALSE, 0, 0, 0);
            emitBlock(conditional.blocks().get(i));
            endJumps.add(emit(Op.JMP, 0, 0, 0));
            patch(falseJump, here());
        }
        if (!conditional.otherwise().isEmpty()) {
            emitBlock(conditional.otherwise());
        }
        int after = here();
        for (int jump : endJumps) {
            patch(jump, after);
        }
    }

    private void whileStatement(Ast.While loop) {
        int start = here();
        emitExpr(loop.condition());
        int falseJump = emit(Op.JMP_FALSE, 0, 0, 0);
        breakJumps.push(new ArrayList<>());
        emitBlock(loop.body());
        emit(Op.JMP, start, 0, 0);
        patch(falseJump, here());
        closeBreaks();
    }

    private void repeatStatement(Ast.Repeat loop) {
        int start = here();
        breakJumps.push(new ArrayList<>());
        int mark = mark();
        for (Ast.Stmt statement : loop.body()) {
            emitStatement(statement);
        }
        // The until condition sees the body's locals, so the scope is restored after it.
        emitExpr(loop.condition());
        int trueJump = emit(Op.JMP_FALSE, 0, 0, 0);
        // JMP_FALSE jumps when false: for repeat, false means loop again.
        patch(trueJump, start);
        restore(mark);
        closeBreaks();
    }

    private void closeBreaks() {
        for (int jump : breakJumps.pop()) {
            patch(jump, here());
        }
    }

    private void numericFor(Ast.NumericFor loop) {
        int outer = mark();
        int startSlot = temp();
        emitExpr(loop.start());
        emit(Op.STORE_LOCAL, startSlot, 0, 0);
        int limitSlot = temp();
        emitExpr(loop.limit());
        emit(Op.STORE_LOCAL, limitSlot, 0, 0);
        int stepSlot = temp();
        if (loop.step() != null) {
            emitExpr(loop.step());
        } else {
            emit(Op.PUSH_CONST, constant(1.0D), 0, 0);
        }
        emit(Op.STORE_LOCAL, stepSlot, 0, 0);
        int control = declare(loop.name());
        int init = emit(Op.FOR_NUM_INIT, startSlot, 0, 0);
        int loopStart = here();
        breakJumps.push(new ArrayList<>());
        emitBlock(loop.body());
        emit(Op.FOR_NUM_NEXT, startSlot, loopStart, 0);
        patchTarget(init, here());
        closeBreaks();
        restore(outer);
        if (control != startSlot + 3) {
            throw new LuaError("internal: numeric for slot layout");
        }
    }

    private void genericFor(Ast.GenericFor loop) {
        int outer = mark();
        int iteratorSlot = temp();
        int stateSlot = temp();
        int controlSlot = temp();
        int varBase = fn.nextSlot;
        for (String name : loop.names()) {
            declare(name);
        }
        // The iterator expression produces up to three values (function, state, control).
        List<Integer> iteratorSlots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            iteratorSlots.add(iteratorSlot + i);
        }
        emitValuesInto(loop.values(), iteratorSlots);
        int init = emit(Op.FOR_GEN_INIT, iteratorSlot, 0, loop.names().size());
        int loopStart = here();
        breakJumps.push(new ArrayList<>());
        emitBlock(loop.body());
        emit(Op.FOR_GEN_NEXT, iteratorSlot, loopStart, loop.names().size());
        patchTarget(init, here());
        closeBreaks();
        restore(outer);
        if (varBase != iteratorSlot + 3) {
            throw new LuaError("internal: generic for slot layout");
        }
    }

    private void returnStatement(Ast.Return statement) {
        List<Ast.Expr> values = statement.values();
        if (values.isEmpty()) {
            emit(Op.RETURN_NONE, 0, 0, 0);
            return;
        }
        Ast.Expr last = values.get(values.size() - 1);
        boolean lastExpands = last instanceof Ast.Call || last instanceof Ast.MethodCall
                || last instanceof Ast.Vararg;
        int singles = lastExpands ? values.size() - 1 : values.size();
        for (int i = 0; i < singles; i++) {
            emitExpr(values.get(i));
        }
        if (lastExpands) {
            if (last instanceof Ast.Vararg) {
                emit(Op.VARARG_ALL, 0, 0, 0);
            } else {
                emitCall(last, true);
            }
            emit(Op.RETURN_MULTI, singles, 0, 0);
        } else {
            emit(Op.RETURN, singles, 0, 0);
        }
    }

    // ------------------------------------------------------------------ expressions

    private void emitExpr(Ast.Expr expression) {
        if (expression instanceof Ast.Nil) {
            emit(Op.PUSH_NIL, 0, 0, 0);
        } else if (expression instanceof Ast.True) {
            emit(Op.PUSH_TRUE, 0, 0, 0);
        } else if (expression instanceof Ast.False) {
            emit(Op.PUSH_FALSE, 0, 0, 0);
        } else if (expression instanceof Ast.Num number) {
            emit(Op.PUSH_CONST, constant(number.value()), 0, 0);
        } else if (expression instanceof Ast.Str text) {
            emit(Op.PUSH_CONST, constant(text.value()), 0, 0);
        } else if (expression instanceof Ast.Vararg) {
            emit(Op.VARARG, 1, 0, 0);
        } else if (expression instanceof Ast.Name name) {
            int local = resolveLocal(name.name());
            if (local >= 0) {
                emit(Op.LOAD_LOCAL, local, 0, 0);
                return;
            }
            int upvalue = resolveUpvalue(name.name());
            if (upvalue >= 0) {
                emit(Op.LOAD_UPVAL, upvalue, 0, 0);
                return;
            }
            emit(Op.LOAD_GLOBAL, constant(name.name()), 0, 0);
        } else if (expression instanceof Ast.Function function) {
            emitFunction(function.def());
        } else if (expression instanceof Ast.Field field) {
            emitExpr(field.table());
            emit(Op.GET_FIELD, constant(field.name()), 0, 0);
        } else if (expression instanceof Ast.Index index) {
            emitExpr(index.table());
            emitExpr(index.key());
            emit(Op.GET_INDEX, 0, 0, 0);
        } else if (expression instanceof Ast.Call || expression instanceof Ast.MethodCall) {
            emitCall(expression, false);
        } else if (expression instanceof Ast.BinOp binary) {
            emitBinary(binary);
        } else if (expression instanceof Ast.UnOp unary) {
            emitExpr(unary.operand());
            switch (unary.op()) {
                case "-" -> emit(Op.NEG, 0, 0, 0);
                case "not" -> emit(Op.NOT, 0, 0, 0);
                case "#" -> emit(Op.LEN, 0, 0, 0);
                default -> throw new LuaError("unsupported unary operator " + unary.op());
            }
        } else if (expression instanceof Ast.Table table) {
            emitTable(table);
        } else {
            throw new LuaError("unsupported expression " + expression.getClass().getSimpleName());
        }
    }

    private void emitBinary(Ast.BinOp binary) {
        String op = binary.op();
        if (op.equals("and")) {
            emitExpr(binary.left());
            int jump = emit(Op.JMP_FALSE_KEEP, 0, 0, 0);
            emit(Op.POP, 0, 0, 0);
            emitExpr(binary.right());
            patch(jump, here());
            return;
        }
        if (op.equals("or")) {
            emitExpr(binary.left());
            int jump = emit(Op.JMP_TRUE_KEEP, 0, 0, 0);
            emit(Op.POP, 0, 0, 0);
            emitExpr(binary.right());
            patch(jump, here());
            return;
        }
        emitExpr(binary.left());
        emitExpr(binary.right());
        switch (op) {
            case "+" -> emit(Op.ADD, 0, 0, 0);
            case "-" -> emit(Op.SUB, 0, 0, 0);
            case "*" -> emit(Op.MUL, 0, 0, 0);
            case "/" -> emit(Op.DIV, 0, 0, 0);
            case "%" -> emit(Op.MOD, 0, 0, 0);
            case "^" -> emit(Op.POW, 0, 0, 0);
            case ".." -> emit(Op.CONCAT, 2, 0, 0);
            case "==" -> emit(Op.EQ, 0, 0, 0);
            case "~=" -> emit(Op.NE, 0, 0, 0);
            case "<" -> emit(Op.LT, 0, 0, 0);
            case "<=" -> emit(Op.LE, 0, 0, 0);
            case ">" -> emit(Op.GT, 0, 0, 0);
            case ">=" -> emit(Op.GE, 0, 0, 0);
            default -> throw new LuaError("unsupported operator " + op);
        }
    }

    private void emitTable(Ast.Table table) {
        emit(Op.NEW_TABLE, 0, 0, 0);
        int arrayIndex = 0;
        List<Ast.TableField> fields = table.fields();
        for (int index = 0; index < fields.size(); index++) {
            Ast.TableField field = fields.get(index);
            boolean lastExpands = field.array() && index == fields.size() - 1 && expands(field.value());
            emit(Op.DUP, 0, 0, 0);
            if (field.array()) {
                arrayIndex++;
                if (lastExpands) {
                    if (field.value() instanceof Ast.Vararg) {
                        emit(Op.VARARG_ALL, 0, 0, 0);
                    } else {
                        emitCall(field.value(), true);
                    }
                    emit(Op.APPEND_MULTI, arrayIndex, 0, 0);
                    continue;
                }
                emit(Op.PUSH_CONST, constant((double) arrayIndex), 0, 0);
                emitExpr(field.value());
                emit(Op.SET_INDEX, 0, 0, 0);
            } else if (field.key() instanceof Ast.Str name) {
                emitExpr(field.value());
                emit(Op.SET_FIELD, constant(name.value()), 0, 0);
            } else {
                emitExpr(field.key());
                emitExpr(field.value());
                emit(Op.SET_INDEX, 0, 0, 0);
            }
        }
    }

    /** Calls: one result normally, all results when it is the tail of a return. */
    private void emitCall(Ast.Expr expression, boolean expand) {
        if (expression instanceof Ast.MethodCall method) {
            emitExpr(method.target());
            int fixed = emitArguments(method.args());
            int nameIndex = constant(method.name());
            if (expand) {
                emit(Op.METHOD_CALL_MULTI, fixed, nameIndex, -1);
            } else if (fixed < 0) {
                emit(Op.METHOD_CALL_TAIL, -fixed - 1, nameIndex, 0);
            } else {
                emit(Op.METHOD_CALL, fixed, nameIndex, 0);
            }
            return;
        }
        Ast.Call call = (Ast.Call) expression;
        emitExpr(call.target());
        int fixed = emitArguments(call.args());
        if (expand) {
            emit(Op.CALL_ALL, call.args().size(), 0, 0);
        } else if (fixed < 0) {
            emit(Op.CALL_TAIL, -fixed - 1, 0, 0);
        } else {
            emit(Op.CALL, fixed, 0, 0);
        }
    }

    /**
     * Push arguments, expanding a trailing call or {@code ...}.
     *
     * @return the number of fixed arguments, or {@code -(fixed + 1)} when the last argument was expanded
     *         (the machine adds the multi count at run time)
     */
    private int emitArguments(List<Ast.Expr> args) {
        if (!args.isEmpty() && expands(args.get(args.size() - 1))) {
            for (int i = 0; i < args.size() - 1; i++) {
                emitExpr(args.get(i));
            }
            Ast.Expr last = args.get(args.size() - 1);
            if (last instanceof Ast.Vararg) {
                emit(Op.VARARG_ALL, 0, 0, 0);
            } else {
                emitCall(last, true);
            }
            return -(args.size());
        }
        for (Ast.Expr argument : args) {
            emitExpr(argument);
        }
        return args.size();
    }

    private void emitFunction(Ast.FunctionDef definition) {
        Fn parent = fn;
        Fn child = new Fn(parent);
        child.vararg = definition.vararg();
        child.proto.vararg = definition.vararg();
        child.proto.params = definition.params().size();
        child.proto.source = parent.proto.source;
        fn = child;
        for (String parameter : definition.params()) {
            declare(parameter);
        }
        int mark = mark();
        for (Ast.Stmt statement : definition.body()) {
            emitStatement(statement);
        }
        restore(mark);
        emit(Op.RETURN_NONE, 0, 0, 0);
        child.proto.maxSlots = Math.max(1, child.nextSlot + 1);
        fn = parent;

        int protoIndex = parent.proto.protos.size();
        parent.proto.protos.add(child.proto);
        List<Integer> spec = new ArrayList<>();
        spec.add(protoIndex);
        spec.add(child.upvalues.size());
        for (int[] upvalue : child.upvalues) {
            spec.add(upvalue[0]);
            spec.add(upvalue[1]);
        }
        parent.proto.closures.add(spec.stream().mapToInt(Integer::intValue).toArray());
        emit(Op.MAKE_CLOSURE, parent.proto.closures.size() - 1, 0, 0);
    }

    /** Find (or create) an upvalue index for a name that is not local to this function. */
    private int resolveUpvalue(String name) {
        return resolveUpvalue(fn, name);
    }

    private int resolveUpvalue(Fn current, String name) {
        if (current.parent == null) {
            return -1;
        }
        for (int i = 0; i < current.upvalues.size(); i++) {
            int[] descriptor = current.upvalues.get(i);
            int candidate = indexOfLocal(current.parent, name);
            if (candidate >= 0 && descriptor[0] == 0 && descriptor[1] == candidate) {
                return i;
            }
        }
        int parentLocal = indexOfLocal(current.parent, name);
        if (parentLocal >= 0) {
            current.upvalues.add(new int[]{0, parentLocal});
            return current.upvalues.size() - 1;
        }
        int parentUpvalue = resolveUpvalue(current.parent, name);
        if (parentUpvalue >= 0) {
            current.upvalues.add(new int[]{1, parentUpvalue});
            return current.upvalues.size() - 1;
        }
        return -1;
    }

    private static int indexOfLocal(Fn function, String name) {
        for (int i = function.locals.size() - 1; i >= 0; i--) {
            if (name.equals(function.locals.get(i))) {
                return function.localSlots.get(i);
            }
        }
        return -1;
    }
}
