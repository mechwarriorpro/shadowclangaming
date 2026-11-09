package com.shadowclangaming.lua;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the supported Lua subset.
 *
 * Grammar is the Lua 5.1 manual's, minus the parts listed in docs/LUA.md as unsupported. The token list is
 * materialised first, so two-token lookahead is just an index and there is no cursor to restore.
 */
public final class Parser {

    private final List<Lexer.Token> tokens;
    private final String sourceName;
    private int at;

    private Parser(List<Lexer.Token> tokens, String sourceName) {
        this.tokens = tokens;
        this.sourceName = sourceName;
    }

    public static Ast.Chunk parse(String source, String sourceName) {
        Parser parser = new Parser(Lexer.tokenize(source), sourceName);
        List<Ast.Stmt> body = parser.block();
        parser.expect(Lexer.EOF, "end of script");
        return new Ast.Chunk(body);
    }

    // ------------------------------------------------------------------ token plumbing

    private Lexer.Token peek() {
        return tokens.get(Math.min(at, tokens.size() - 1));
    }

    private Lexer.Token peek(int ahead) {
        return tokens.get(Math.min(at + ahead, tokens.size() - 1));
    }

    private boolean check(int type) {
        return peek().type == type;
    }

    private boolean accept(int type) {
        if (check(type)) {
            at++;
            return true;
        }
        return false;
    }

    private Lexer.Token expect(int type, String what) {
        if (!check(type)) {
            throw error("expected " + what + " but found " + describe(peek()));
        }
        Lexer.Token token = peek();
        at++;
        return token;
    }

    private static String describe(Lexer.Token token) {
        return switch (token.type) {
            case Lexer.NAME -> "'" + token.text + "'";
            case Lexer.STRING -> "a string";
            case Lexer.NUMBER -> "'" + token.text + "'";
            case Lexer.EOF -> "end of script";
            default -> "'" + Lexer.name(token.type) + "'";
        };
    }

    private LuaError error(String message) {
        return new LuaError(sourceName + ":" + peek().line + ": " + message);
    }

    // ------------------------------------------------------------------ statements

    private List<Ast.Stmt> block() {
        List<Ast.Stmt> body = new ArrayList<>();
        while (!endsBlock()) {
            if (accept(Lexer.SEMI)) {
                continue;
            }
            Ast.Stmt statement = statement();
            if (statement != null) {
                body.add(statement);
            }
            accept(Lexer.SEMI);
        }
        return body;
    }

    private boolean endsBlock() {
        int type = peek().type;
        return type == Lexer.EOF || type == Lexer.END || type == Lexer.ELSE || type == Lexer.ELSEIF
                || type == Lexer.UNTIL;
    }

    private Ast.Stmt statement() {
        return switch (peek().type) {
            case Lexer.IF -> ifStatement();
            case Lexer.WHILE -> whileStatement();
            case Lexer.DO -> {
                at++;
                List<Ast.Stmt> body = block();
                expect(Lexer.END, "'end'");
                yield new Ast.Do(body);
            }
            case Lexer.REPEAT -> repeatStatement();
            case Lexer.FOR -> forStatement();
            case Lexer.FUNCTION -> functionStatement();
            case Lexer.LOCAL -> localStatement();
            case Lexer.RETURN -> {
                at++;
                List<Ast.Expr> values = new ArrayList<>();
                if (!endsBlock() && !check(Lexer.SEMI)) {
                    values = exprList();
                }
                yield new Ast.Return(values);
            }
            case Lexer.BREAK -> {
                at++;
                yield new Ast.Break();
            }
            case Lexer.NAME, Lexer.LPAREN -> expressionStatement();
            default -> throw error("unexpected " + describe(peek()) + " at the start of a statement");
        };
    }

    private Ast.Stmt ifStatement() {
        expect(Lexer.IF, "'if'");
        List<Ast.Expr> conditions = new ArrayList<>();
        List<List<Ast.Stmt>> blocks = new ArrayList<>();
        conditions.add(expr());
        expect(Lexer.THEN, "'then'");
        blocks.add(block());
        while (accept(Lexer.ELSEIF)) {
            conditions.add(expr());
            expect(Lexer.THEN, "'then'");
            blocks.add(block());
        }
        List<Ast.Stmt> otherwise = List.of();
        if (accept(Lexer.ELSE)) {
            otherwise = block();
        }
        expect(Lexer.END, "'end'");
        return new Ast.If(conditions, blocks, otherwise);
    }

    private Ast.Stmt whileStatement() {
        expect(Lexer.WHILE, "'while'");
        Ast.Expr condition = expr();
        expect(Lexer.DO, "'do'");
        List<Ast.Stmt> body = block();
        expect(Lexer.END, "'end'");
        return new Ast.While(condition, body);
    }

    private Ast.Stmt repeatStatement() {
        expect(Lexer.REPEAT, "'repeat'");
        List<Ast.Stmt> body = block();
        expect(Lexer.UNTIL, "'until'");
        return new Ast.Repeat(body, expr());
    }

    private Ast.Stmt forStatement() {
        expect(Lexer.FOR, "'for'");
        String firstName = expect(Lexer.NAME, "a loop variable").text;
        if (accept(Lexer.ASSIGN)) {
            Ast.Expr start = expr();
            expect(Lexer.COMMA, "','");
            Ast.Expr limit = expr();
            Ast.Expr step = accept(Lexer.COMMA) ? expr() : null;
            expect(Lexer.DO, "'do'");
            List<Ast.Stmt> body = block();
            expect(Lexer.END, "'end'");
            return new Ast.NumericFor(firstName, start, limit, step, body);
        }
        List<String> names = new ArrayList<>();
        names.add(firstName);
        while (accept(Lexer.COMMA)) {
            names.add(expect(Lexer.NAME, "a loop variable").text);
        }
        expect(Lexer.IN, "'in'");
        List<Ast.Expr> values = exprList();
        expect(Lexer.DO, "'do'");
        List<Ast.Stmt> body = block();
        expect(Lexer.END, "'end'");
        return new Ast.GenericFor(names, values, body);
    }

    private Ast.Stmt functionStatement() {
        expect(Lexer.FUNCTION, "'function'");
        // function a.b.c() / function a:m() are sugar for a.b.c = function() / a.m = function(self)
        List<String> path = new ArrayList<>();
        path.add(expect(Lexer.NAME, "a function name").text);
        boolean method = false;
        while (true) {
            if (accept(Lexer.DOT)) {
                path.add(expect(Lexer.NAME, "a field name").text);
                continue;
            }
            if (accept(Lexer.COLON)) {
                path.add(expect(Lexer.NAME, "a method name").text);
                method = true;
            }
            break;
        }
        Ast.FunctionDef definition = functionBody(method);
        Ast.Expr target = new Ast.Name(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            target = new Ast.Field(target, path.get(i));
        }
        return new Ast.Assign(List.of(target), List.of(new Ast.Function(definition)));
    }

    private Ast.Stmt localStatement() {
        expect(Lexer.LOCAL, "'local'");
        if (accept(Lexer.FUNCTION)) {
            String name = expect(Lexer.NAME, "a function name").text;
            return new Ast.LocalFunction(name, functionBody(false));
        }
        List<String> names = new ArrayList<>();
        while (true) {
            names.add(expect(Lexer.NAME, "a local name").text);
            if (check(Lexer.LT)) {
                // Lua 5.4 attributes: parsed and ignored.
                at++;
                expect(Lexer.NAME, "an attribute");
                expect(Lexer.GT, "'>'");
            }
            if (!accept(Lexer.COMMA)) {
                break;
            }
        }
        List<Ast.Expr> values = accept(Lexer.ASSIGN) ? exprList() : List.of();
        return new Ast.LocalAssign(names, values);
    }

    private Ast.Stmt expressionStatement() {
        Ast.Expr expression = suffixedExpression();
        if (check(Lexer.ASSIGN) || check(Lexer.COMMA)) {
            List<Ast.Expr> targets = new ArrayList<>();
            targets.add(asTarget(expression));
            while (accept(Lexer.COMMA)) {
                targets.add(asTarget(suffixedExpression()));
            }
            expect(Lexer.ASSIGN, "'='");
            return new Ast.Assign(targets, exprList());
        }
        if (!(expression instanceof Ast.Call) && !(expression instanceof Ast.MethodCall)) {
            throw error("this expression does nothing - did you mean to call it or assign it?");
        }
        return new Ast.CallStmt(expression);
    }

    private Ast.Expr asTarget(Ast.Expr expression) {
        if (expression instanceof Ast.Name || expression instanceof Ast.Index
                || expression instanceof Ast.Field) {
            return expression;
        }
        throw error("cannot assign to this expression");
    }

    private Ast.FunctionDef functionBody(boolean method) {
        expect(Lexer.LPAREN, "'('");
        List<String> params = new ArrayList<>();
        if (method) {
            params.add("self");
        }
        boolean vararg = false;
        if (!check(Lexer.RPAREN)) {
            while (true) {
                if (accept(Lexer.VARARG)) {
                    vararg = true;
                    break;
                }
                params.add(expect(Lexer.NAME, "a parameter name").text);
                if (!accept(Lexer.COMMA)) {
                    break;
                }
            }
        }
        expect(Lexer.RPAREN, "')'");
        List<Ast.Stmt> body = block();
        expect(Lexer.END, "'end'");
        return new Ast.FunctionDef(params, vararg, body);
    }

    // ------------------------------------------------------------------ expressions

    private List<Ast.Expr> exprList() {
        List<Ast.Expr> values = new ArrayList<>();
        values.add(expr());
        while (accept(Lexer.COMMA)) {
            values.add(expr());
        }
        return values;
    }

    private Ast.Expr expr() {
        return subExpression(0);
    }

    private static int leftPrecedence(int type) {
        return switch (type) {
            case Lexer.OR -> 1;
            case Lexer.AND -> 2;
            case Lexer.LT, Lexer.GT, Lexer.LE, Lexer.GE, Lexer.NE, Lexer.EQ -> 3;
            case Lexer.CONCAT -> 4;
            case Lexer.PLUS, Lexer.MINUS -> 5;
            case Lexer.STAR, Lexer.SLASH, Lexer.PERCENT -> 6;
            case Lexer.CARET -> 8;
            default -> 0;
        };
    }

    private static String opName(int type) {
        return switch (type) {
            case Lexer.PLUS -> "+";
            case Lexer.MINUS -> "-";
            case Lexer.STAR -> "*";
            case Lexer.SLASH -> "/";
            case Lexer.PERCENT -> "%";
            case Lexer.CARET -> "^";
            case Lexer.CONCAT -> "..";
            case Lexer.EQ -> "==";
            case Lexer.NE -> "~=";
            case Lexer.LT -> "<";
            case Lexer.LE -> "<=";
            case Lexer.GT -> ">";
            case Lexer.GE -> ">=";
            case Lexer.AND -> "and";
            case Lexer.OR -> "or";
            default -> "?";
        };
    }

    private Ast.Expr subExpression(int limit) {
        Ast.Expr left = unaryExpression();
        while (true) {
            int type = peek().type;
            int precedence = leftPrecedence(type);
            if (precedence == 0 || precedence <= limit) {
                return left;
            }
            at++;
            // '^' and '..' are right-associative
            int nextLimit = (type == Lexer.CARET || type == Lexer.CONCAT) ? precedence - 1 : precedence;
            Ast.Expr right = subExpression(nextLimit);
            left = new Ast.BinOp(opName(type), left, right);
        }
    }

    private Ast.Expr unaryExpression() {
        if (check(Lexer.NOT) || check(Lexer.MINUS) || check(Lexer.HASH)) {
            String op = check(Lexer.NOT) ? "not" : check(Lexer.MINUS) ? "-" : "#";
            at++;
            Ast.Expr operand = subExpression(7);
            return new Ast.UnOp(op, operand);
        }
        return simpleExpression();
    }

    private Ast.Expr simpleExpression() {
        Lexer.Token token = peek();
        switch (token.type) {
            case Lexer.NIL -> {
                at++;
                return new Ast.Nil();
            }
            case Lexer.TRUE -> {
                at++;
                return new Ast.True();
            }
            case Lexer.FALSE -> {
                at++;
                return new Ast.False();
            }
            case Lexer.NUMBER -> {
                at++;
                return new Ast.Num(token.number);
            }
            case Lexer.STRING -> {
                at++;
                return new Ast.Str(token.text);
            }
            case Lexer.VARARG -> {
                at++;
                return new Ast.Vararg();
            }
            case Lexer.FUNCTION -> {
                at++;
                return new Ast.Function(functionBody(false));
            }
            case Lexer.LBRACE -> {
                return tableConstructor();
            }
            case Lexer.NAME, Lexer.LPAREN -> {
                return suffixedExpression();
            }
            default -> throw error("unexpected " + describe(token) + " in an expression");
        }
    }

    private Ast.Expr suffixedExpression() {
        Ast.Expr expression;
        if (check(Lexer.NAME)) {
            expression = new Ast.Name(peek().text);
            at++;
        } else {
            expect(Lexer.LPAREN, "'('");
            expression = expr();
            expect(Lexer.RPAREN, "')'");
        }
        while (true) {
            switch (peek().type) {
                case Lexer.DOT -> {
                    at++;
                    expression = new Ast.Field(expression, expect(Lexer.NAME, "a field name").text);
                }
                case Lexer.LBRACKET -> {
                    at++;
                    Ast.Expr key = expr();
                    expect(Lexer.RBRACKET, "']'");
                    expression = new Ast.Index(expression, key);
                }
                case Lexer.COLON -> {
                    at++;
                    String method = expect(Lexer.NAME, "a method name").text;
                    expression = new Ast.MethodCall(expression, method, callArgs());
                }
                case Lexer.LPAREN, Lexer.STRING, Lexer.LBRACE -> expression =
                        new Ast.Call(expression, callArgs());
                default -> {
                    return expression;
                }
            }
        }
    }

    private List<Ast.Expr> callArgs() {
        if (check(Lexer.STRING)) {
            String text = peek().text;
            at++;
            return List.of(new Ast.Str(text));
        }
        if (check(Lexer.LBRACE)) {
            return List.of(tableConstructor());
        }
        expect(Lexer.LPAREN, "'('");
        List<Ast.Expr> args = check(Lexer.RPAREN) ? List.of() : exprList();
        expect(Lexer.RPAREN, "')'");
        return args;
    }

    private Ast.Expr tableConstructor() {
        expect(Lexer.LBRACE, "'{'");
        List<Ast.TableField> fields = new ArrayList<>();
        while (!check(Lexer.RBRACE)) {
            if (check(Lexer.LBRACKET)) {
                at++;
                Ast.Expr key = expr();
                expect(Lexer.RBRACKET, "']'");
                expect(Lexer.ASSIGN, "'='");
                fields.add(new Ast.TableField(key, expr()));
            } else if (check(Lexer.NAME) && peek(1).type == Lexer.ASSIGN) {
                String name = peek().text;
                at += 2;
                fields.add(new Ast.TableField(new Ast.Str(name), expr()));
            } else {
                fields.add(new Ast.TableField(null, expr()));
            }
            if (!accept(Lexer.COMMA) && !accept(Lexer.SEMI)) {
                break;
            }
        }
        expect(Lexer.RBRACE, "'}'");
        return new Ast.Table(fields);
    }
}
