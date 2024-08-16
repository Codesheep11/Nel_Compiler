package frontend.lexer;
import java.util.ArrayList;
import java.util.Arrays;

import frontend.exception.*;

public class TokenArray {
    public final ArrayList<Token> tokens = new ArrayList<>();
    private static final boolean DEBUG_MODE = false;

    public int index = 0;


    public void append(Token element) {
        tokens.add(element);
    }

    public Token getToken() {
        return tokens.get(index);
    }

    public void setToken(int index, Token newElement) {
        tokens.set(index, newElement);
    }

    public int getSize() {
        return tokens.size();
    }

    public Token next() {
        index += 1;
        return tokens.get(index);
    }

    public Token ahead(int count) {
        return tokens.get(index + count);
    }

    public boolean isEnd() {
        return index >= tokens.size() || tokens.get(index).tokenType == TokenType.EOF;
    }


    public Token consumeToken(TokenType type) throws SyntaxError {
        if(isEnd()) {
            throw new SyntaxError("Unexpected EOF");
        }
        Token token = tokens.get(index);
        if(token.tokenType == type) {
            if(DEBUG_MODE) {
                System.err.println("consume: "+ token.tokenType.toString());
            }
            index ++;
            return token;
        }
        throw new SyntaxError("Expected " + type + " but got " + token.tokenType.toString());
    }

    public boolean checkAndSkip(TokenType type) {
        Token token = tokens.get(index);
        if(token.tokenType == type) {
            index ++;
            return true;
        }
        return false;
    }

    public boolean checkAndSkip(TokenType... types) {
        Token token = tokens.get(index);
        for (TokenType type:
             types) {
            if(token.tokenType == type) {
                index ++;
                return true;
            }
        }
        return false;
    }

    public Token consumeToken(TokenType... types) throws SyntaxError {
        if(isEnd()) {
            throw new SyntaxError("Unexpected EOF");
        }
        Token token = tokens.get(index);
        for (TokenType type : types) {
            if (token.tokenType == type) {
                if (DEBUG_MODE) {
                    System.err.println("consume: " + type.toString());
                }
                index++;
                return token;
            }
        }
        for(int i = 0;i < index;i ++) {
            System.err.println(tokens.get(i));
        }
        throw new SyntaxError("Expected " + Arrays.toString(types) + " but got " + token.tokenType.toString());
    }

    public boolean check(TokenType... types) {
        Token token = tokens.get(index);
        for (TokenType type : types) {
            if (token.tokenType == type) {
                return true;
            }
        }
        return false;
    }

    public boolean check(int count, TokenType... types) {
        if (index + count >= tokens.size()) {
            return false;
        }
        Token token = tokens.get(index + count);
        for (TokenType type : types) {
            if (token.tokenType == type) {
                return true;
            }
        }
        return false;
    }
}
