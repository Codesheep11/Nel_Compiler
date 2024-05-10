package frontend.lexer;


public class Token {
    public TokenType tokenType;
    public String content;

    public Token(TokenType tokenType, String content) {
        this.tokenType = tokenType;
        this.content = content;
    }
}
