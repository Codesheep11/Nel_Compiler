package frontend.lexer;


public class Token {
    public static int lineNum = 1;
    public final TokenType tokenType;
    public final String content;
    public final int line;

    public Token(TokenType tokenType, String content) {
        this.tokenType = tokenType;
        this.content = content;
        this.line = lineNum;
    }
}
