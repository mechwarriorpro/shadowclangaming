package com.shadowclangaming.lua;

import java.util.HashMap;
import java.util.Map;

/**
 * Lua lexer.
 *
 * Hand-written and deliberately boring: keywords, names, numbers (decimal, exponent, hex), quoted strings
 * with the escapes that matter, and long-bracket strings/comments ({@code [[ ... ]]}, {@code --[==[ ... ]==]}).
 * Anything it does not understand is an error with a line number, because a script that silently
 * mis-lexes is a nightmare to debug from inside a game.
 */
public final class Lexer {

    public static final int EOF = 0;
    public static final int NAME = 1;
    public static final int NUMBER = 2;
    public static final int STRING = 3;

    public static final int AND = 10;
    public static final int BREAK = 11;
    public static final int DO = 12;
    public static final int ELSE = 13;
    public static final int ELSEIF = 14;
    public static final int END = 15;
    public static final int FALSE = 16;
    public static final int FOR = 17;
    public static final int FUNCTION = 18;
    public static final int IF = 19;
    public static final int IN = 20;
    public static final int LOCAL = 21;
    public static final int NIL = 22;
    public static final int NOT = 23;
    public static final int OR = 24;
    public static final int REPEAT = 25;
    public static final int RETURN = 26;
    public static final int THEN = 27;
    public static final int TRUE = 28;
    public static final int UNTIL = 29;
    public static final int WHILE = 30;

    public static final int PLUS = 50;
    public static final int MINUS = 51;
    public static final int STAR = 52;
    public static final int SLASH = 53;
    public static final int PERCENT = 54;
    public static final int CARET = 55;
    public static final int HASH = 56;
    public static final int EQ = 57;
    public static final int NE = 58;
    public static final int LE = 59;
    public static final int GE = 60;
    public static final int LT = 61;
    public static final int GT = 62;
    public static final int ASSIGN = 63;
    public static final int LPAREN = 64;
    public static final int RPAREN = 65;
    public static final int LBRACE = 66;
    public static final int RBRACE = 67;
    public static final int LBRACKET = 68;
    public static final int RBRACKET = 69;
    public static final int SEMI = 70;
    public static final int COLON = 71;
    public static final int COMMA = 72;
    public static final int DOT = 73;
    public static final int CONCAT = 74;
    public static final int VARARG = 75;

    private static final Map<String, Integer> KEYWORDS = new HashMap<>();

    static {
        String[] names = {"and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if",
                "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"};
        int[] codes = {AND, BREAK, DO, ELSE, ELSEIF, END, FALSE, FOR, FUNCTION, IF, IN, LOCAL, NIL, NOT,
                OR, REPEAT, RETURN, THEN, TRUE, UNTIL, WHILE};
        for (int i = 0; i < names.length; i++) {
            KEYWORDS.put(names[i], codes[i]);
        }
    }

    /** One token. Text is the name/string, number is set for NUMBER. */
    public static final class Token {
        public final int type;
        public final String text;
        public final double number;
        public final int line;

        Token(int type, String text, double number, int line) {
            this.type = type;
            this.text = text;
            this.number = number;
            this.line = line;
        }

        @Override
        public String toString() {
            return type == NAME || type == STRING ? text : "token(" + type + ")";
        }
    }

    private final String source;
    private int position;
    private int line = 1;

    public Token token;

    public Lexer(String source) {
        this.source = source == null ? "" : source;
        this.token = lex();
    }

    /** Advance to the next token and return it. */
    public Token next() {
        token = lex();
        return token;
    }

    /**
     * Lex the whole source up front.
     *
     * The parser wants two tokens of lookahead in a few places (telling {@code name = value} from an
     * expression inside a table constructor, for instance). Tokenising eagerly makes that trivial and
     * keeps the parser free of lexer cursor juggling, and a script is a few kilobytes at most.
     */
    public static java.util.List<Token> tokenize(String source) {
        Lexer lexer = new Lexer(source);
        java.util.List<Token> tokens = new java.util.ArrayList<>();
        tokens.add(lexer.token);
        while (lexer.token.type != EOF) {
            tokens.add(lexer.next());
        }
        return tokens;
    }

    public String source() {
        return source;
    }

    private char peek() {
        return position < source.length() ? source.charAt(position) : '\0';
    }

    private char peek(int ahead) {
        int at = position + ahead;
        return at < source.length() ? source.charAt(at) : '\0';
    }

    private void advance() {
        if (position < source.length()) {
            if (source.charAt(position) == '\n') {
                line++;
            }
            position++;
        }
    }

    private Token lex() {
        skipSpaceAndComments();
        int at = line;
        char ch = peek();
        if (ch == '\0') {
            return new Token(EOF, "<eof>", 0, at);
        }
        if (Character.isLetter(ch) || ch == '_') {
            StringBuilder name = new StringBuilder();
            while (Character.isLetterOrDigit(peek()) || peek() == '_') {
                name.append(peek());
                advance();
            }
            String text = name.toString();
            Integer keyword = KEYWORDS.get(text);
            return new Token(keyword == null ? NAME : keyword, text, 0, at);
        }
        if (Character.isDigit(ch) || (ch == '.' && Character.isDigit(peek(1)))) {
            return number(at);
        }
        switch (ch) {
            case '"':
            case '\'':
                return new Token(STRING, quoted(ch), 0, at);
            case '[': {
                int level = longBracketLevel();
                if (level >= 0) {
                    return new Token(STRING, longString(level), 0, at);
                }
                advance();
                return new Token(LBRACKET, "[", 0, at);
            }
            case '+':
                advance();
                return new Token(PLUS, "+", 0, at);
            case '-':
                advance();
                return new Token(MINUS, "-", 0, at);
            case '*':
                advance();
                return new Token(STAR, "*", 0, at);
            case '/':
                advance();
                return new Token(SLASH, "/", 0, at);
            case '%':
                advance();
                return new Token(PERCENT, "%", 0, at);
            case '^':
                advance();
                return new Token(CARET, "^", 0, at);
            case '#':
                advance();
                return new Token(HASH, "#", 0, at);
            case '=': {
                advance();
                if (peek() == '=') {
                    advance();
                    return new Token(EQ, "==", 0, at);
                }
                return new Token(ASSIGN, "=", 0, at);
            }
            case '~': {
                advance();
                if (peek() == '=') {
                    advance();
                    return new Token(NE, "~=", 0, at);
                }
                throw new LuaError("unexpected '~' on line " + at);
            }
            case '<': {
                advance();
                if (peek() == '=') {
                    advance();
                    return new Token(LE, "<=", 0, at);
                }
                return new Token(LT, "<", 0, at);
            }
            case '>': {
                advance();
                if (peek() == '=') {
                    advance();
                    return new Token(GE, ">=", 0, at);
                }
                return new Token(GT, ">", 0, at);
            }
            case '(':
                advance();
                return new Token(LPAREN, "(", 0, at);
            case ')':
                advance();
                return new Token(RPAREN, ")", 0, at);
            case '{':
                advance();
                return new Token(LBRACE, "{", 0, at);
            case '}':
                advance();
                return new Token(RBRACE, "}", 0, at);
            case ']':
                advance();
                return new Token(RBRACKET, "]", 0, at);
            case ';':
                advance();
                return new Token(SEMI, ";", 0, at);
            case ':':
                advance();
                return new Token(COLON, ":", 0, at);
            case ',':
                advance();
                return new Token(COMMA, ",", 0, at);
            case '.': {
                advance();
                if (peek() == '.') {
                    advance();
                    if (peek() == '.') {
                        advance();
                        return new Token(VARARG, "...", 0, at);
                    }
                    return new Token(CONCAT, "..", 0, at);
                }
                return new Token(DOT, ".", 0, at);
            }
            default:
                throw new LuaError("unexpected character '" + ch + "' on line " + at);
        }
    }

    private void skipSpaceAndComments() {
        while (true) {
            while (Character.isWhitespace(peek())) {
                advance();
            }
            if (peek() == '-' && peek(1) == '-') {
                advance();
                advance();
                if (peek() == '[') {
                    int level = longBracketLevel();
                    if (level >= 0) {
                        longString(level);
                        continue;
                    }
                }
                while (peek() != '\0' && peek() != '\n') {
                    advance();
                }
                continue;
            }
            return;
        }
    }

    /** If we are sitting on "[=*[", return the number of '=' and consume it, else -1. */
    private int longBracketLevel() {
        if (peek() != '[') {
            return -1;
        }
        int cursor = position + 1;
        int level = 0;
        while (cursor < source.length() && source.charAt(cursor) == '=') {
            level++;
            cursor++;
        }
        if (cursor < source.length() && source.charAt(cursor) == '[') {
            while (position <= cursor) {
                advance();
            }
            return level;
        }
        return -1;
    }

    private String longString(int level) {
        StringBuilder text = new StringBuilder();
        while (true) {
            if (peek() == '\0') {
                throw new LuaError("unfinished long string on line " + line);
            }
            if (peek() == ']') {
                int cursor = position + 1;
                int count = 0;
                while (cursor < source.length() && source.charAt(cursor) == '=') {
                    count++;
                    cursor++;
                }
                if (count == level && cursor < source.length() && source.charAt(cursor) == ']') {
                    for (int i = 0; i <= count + 1; i++) {
                        advance();
                    }
                    return text.toString();
                }
            }
            text.append(peek());
            advance();
        }
    }

    private String quoted(char quote) {
        advance();
        StringBuilder text = new StringBuilder();
        while (true) {
            char ch = peek();
            if (ch == '\0') {
                throw new LuaError("unfinished string on line " + line);
            }
            if (ch == quote) {
                advance();
                return text.toString();
            }
            if (ch == '\\') {
                advance();
                char escape = peek();
                switch (escape) {
                    case 'n' -> text.append('\n');
                    case 't' -> text.append('\t');
                    case 'r' -> text.append('\r');
                    case 'a' -> text.append('\u0007');
                    case 'b' -> text.append('\b');
                    case 'f' -> text.append('\f');
                    case 'v' -> text.append('\u000B');
                    case '0' -> text.append('\0');
                    case '\\' -> text.append('\\');
                    case '"' -> text.append('"');
                    case '\'' -> text.append('\'');
                    case '\n' -> text.append('\n');
                    default -> text.append(escape);
                }
                advance();
                continue;
            }
            text.append(ch);
            advance();
        }
    }

    private Token number(int at) {
        int start = position;
        if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            advance();
            advance();
            int hexStart = position;
            while (Character.digit(peek(), 16) >= 0) {
                advance();
            }
            String digits = source.substring(hexStart, position);
            if (digits.isEmpty()) {
                throw new LuaError("malformed hex number on line " + at);
            }
            return new Token(NUMBER, source.substring(start, position), (double) Long.parseLong(digits, 16), at);
        }
        while (Character.isDigit(peek())) {
            advance();
        }
        if (peek() == '.' && Character.isDigit(peek(1))) {
            advance();
            while (Character.isDigit(peek())) {
                advance();
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            advance();
            if (peek() == '+' || peek() == '-') {
                advance();
            }
            while (Character.isDigit(peek())) {
                advance();
            }
        }
        String text = source.substring(start, position);
        try {
            return new Token(NUMBER, text, Double.parseDouble(text), at);
        } catch (NumberFormatException error) {
            throw new LuaError("malformed number '" + text + "' on line " + at);
        }
    }

    /** Human-readable token name, for parse errors. */
    public static String name(int type) {
        for (Map.Entry<String, Integer> entry : KEYWORDS.entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        return switch (type) {
            case EOF -> "<eof>";
            case NAME -> "<name>";
            case NUMBER -> "<number>";
            case STRING -> "<string>";
            case EQ -> "==";
            case NE -> "~=";
            case LE -> "<=";
            case GE -> ">=";
            case LT -> "<";
            case GT -> ">";
            case ASSIGN -> "=";
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case PERCENT -> "%";
            case CARET -> "^";
            case HASH -> "#";
            case LPAREN -> "(";
            case RPAREN -> ")";
            case LBRACE -> "{";
            case RBRACE -> "}";
            case LBRACKET -> "[";
            case RBRACKET -> "]";
            case SEMI -> ";";
            case COLON -> ":";
            case COMMA -> ",";
            case DOT -> ".";
            case CONCAT -> "..";
            case VARARG -> "...";
            default -> "token(" + type + ")";
        };
    }
}
