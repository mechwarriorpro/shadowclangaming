package com.shadowclangaming.lua;

import java.util.List;

/**
 * The syntax tree.
 *
 * A tree in the middle of the pipeline, rather than the reference implementation's single pass, because
 * this compiler has to be obviously correct while being read by one person: multiple assignment,
 * closures and the {@code repeat ... until} scope rule are all much easier to get right when the shape of
 * the statement is visible before any bytes are emitted.
 *
 * Nothing here is public API; {@link Lua} is the only entry point a block entity should touch.
 */
public final class Ast {

    private Ast() {
    }

    public sealed interface Node permits Stmt, Expr {
    }

    // ------------------------------------------------------------------ statements

    public sealed interface Stmt extends Node {
    }

    public record Chunk(List<Stmt> body) {
    }

    public record LocalAssign(List<String> names, List<Expr> values) implements Stmt {
    }

    public record Assign(List<Expr> targets, List<Expr> values) implements Stmt {
    }

    /** A bare call used as a statement; its results are discarded. */
    public record CallStmt(Expr call) implements Stmt {
    }

    public record Do(List<Stmt> body) implements Stmt {
    }

    public record If(List<Expr> conditions, List<List<Stmt>> blocks, List<Stmt> otherwise) implements Stmt {
    }

    public record While(Expr condition, List<Stmt> body) implements Stmt {
    }

    public record Repeat(List<Stmt> body, Expr condition) implements Stmt {
    }

    public record NumericFor(String name, Expr start, Expr limit, Expr step, List<Stmt> body)
            implements Stmt {
    }

    public record GenericFor(List<String> names, List<Expr> values, List<Stmt> body) implements Stmt {
    }

    public record LocalFunction(String name, FunctionDef function) implements Stmt {
    }

    public record Return(List<Expr> values) implements Stmt {
    }

    public record Break() implements Stmt {
    }

    // ------------------------------------------------------------------ expressions

    public sealed interface Expr extends Node {
    }

    public record Nil() implements Expr {
    }

    public record True() implements Expr {
    }

    public record False() implements Expr {
    }

    public record Num(double value) implements Expr {
    }

    public record Str(String value) implements Expr {
    }

    public record Vararg() implements Expr {
    }

    public record Name(String name) implements Expr {
    }

    public record FunctionDef(List<String> params, boolean vararg, List<Stmt> body) {
    }

    public record Function( FunctionDef def) implements Expr {
    }

    public record BinOp(String op, Expr left, Expr right) implements Expr {
    }

    public record UnOp(String op, Expr operand) implements Expr {
    }

    public record Index(Expr table, Expr key) implements Expr {
    }

    public record Field(Expr table, String name) implements Expr {
    }

    public record Call(Expr target, List<Expr> args) implements Expr {
    }

    public record MethodCall(Expr target, String name, List<Expr> args) implements Expr {
    }

    public record TableField(Expr key, Expr value) {
        public boolean array() {
            return key == null;
        }
    }

    public record Table(List<TableField> fields) implements Expr {
    }
}
