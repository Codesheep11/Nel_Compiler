package frontend.lexer;


public class Token {
    public static int lineNum = 1;
    public TokenType tokenType;
    public String content;
    public int line;

    public Token(TokenType tokenType, String content) {
        this.tokenType = tokenType;
        this.content = content;
        this.line = lineNum;
    }
}
