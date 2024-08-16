package frontend.lexer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;


public class Lexer {
    private final TokenArray tokenArray;
    private final BufferedInputStream src;

    private final StringHandler stringHandler;

    private final boolean debugMode;

    public Lexer(BufferedInputStream src, TokenArray tokenArray) {
        this.src = src;
        this.tokenArray = tokenArray;
        this.stringHandler = new StringHandler(src);
        this.debugMode = false;
    }

    public Lexer(BufferedInputStream src, TokenArray tokenArray, boolean DEBUG_MODE) {
        this.src = src;
        this.tokenArray = tokenArray;
        this.stringHandler = new StringHandler(src);
        this.debugMode = DEBUG_MODE;
    }

    private int detectType(String str) {
        for (TokenType tokenType : TokenType.values()) {
            Pattern p = tokenType.getPattern();
            if (p.matcher(str).matches()) {
                if (debugMode) System.out.println(tokenType + "\t" + str);
                tokenArray.append(new Token(tokenType, str));
                return 0;
            }
        }
        return -1;
    }

    public void lex() throws IOException {
        if (stringHandler.reachEOF()) {
            System.err.println("Try to lex a file which has reached the end!");
            return;
        }

        while (!stringHandler.reachEOF()) {
            String cur = stringHandler.scanf();
            ArrayList<String> subStrings = stringHandler.SplitString(cur);
//            System.out.println(subStrings);
            for (String subString : subStrings) {
                if (detectType(subString) < 0) {
                    throw new RuntimeException("Unexpected token type, content:" + "\t" + subString);
                }
            }
        }
        if (stringHandler.reachEOF()) {
            tokenArray.append(new Token(TokenType.EOF, ""));
            if (debugMode) {
                System.out.println(TokenType.EOF + "\t" + "");
            }
        }
    }
}
