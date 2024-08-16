package frontend.lexer;

import java.util.regex.Pattern;
public enum TokenType {
    INT("int", true),
    FLOAT("float", true),
    VOID("void", true),
    RETURN("return", true),
    CONST("const", true),
    IF("if", true),
    WHILE("while", true),
    BREAK("break", true),
    CONTINUE("continue", true),
    ELSE("else", true),
    IDENTIFIER("[A-Za-z_][A-Za-z0-9_]*"),
    L_PAREN("\\("),
    R_PAREN("\\)"),
    L_BRACE("\\{"),
    R_BRACE("\\}"),
    L_BRACK("\\["),
    R_BRACK("]"),
    LOR("\\|\\|"),
    LAND("&&"),
    EQ("=="),
    NE("!="),
    LE("<="),
    GE(">="),
    LT("<"),
    GT(">"),
    ADD("\\+"),
    SUB("-"),
    MUL("\\*"),
    DIV("/"),
    MOD("%"),
    NOT("!"),
    ASSIGN("="),
    COMMA(","),
    DEC_FLOAT("([0-9]*\\.[0-9]*((p|P|e|E)(\\+|\\-)?[0-9]+)?)|" +
            "([0-9]*[\\.]?[0-9]*(p|P|e|E)((\\+|\\-)?[0-9]+)?)"),
    HEX_FLOAT("(0(x|X)[0-9A-Fa-f]*\\.[0-9A-Fa-f]*((p|P|e|E)(\\+|\\-)?[0-9A-Fa-f]*)?)|" +
            "(0(x|X)[0-9A-Fa-f]*[\\.]?[0-9A-Fa-f]*(p|P|e|E)((\\+|\\-)?[0-9A-Fa-f]*)?)"),
    DEC_INT("0|([1-9][0-9]*)"),
    HEX_INT("0(x|X)[0-9A-Fa-f]+"),
    OCT_INT("0[0-7]+"),
    SEMI(";"),
    STR("\"[^\"]*\""),
    EOF("");


    private final String content;
    private final boolean reserved;

    TokenType(String content) {
        this.content = content;
        this.reserved = false;
    }

    TokenType(String content, boolean reserved) {
        this.content = content;
        this.reserved = reserved;
    }

    public Pattern getPattern() {
        return Pattern.compile("^(" + content + ")" + (reserved ? "(?!\\w)" : ""));
    }
}
