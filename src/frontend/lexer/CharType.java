package frontend.lexer;

public class CharType {
    public CharType() {

    }

    public boolean isdigit(char c) {
        return Character.isDigit(c);
    }

    public boolean isupper(char c) {
        return Character.isUpperCase(c);
    }

    public boolean islower(char c) {
        return Character.isLowerCase(c);
    }

    public boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    public boolean isIdent(char c) {
        return isdigit(c) || isLetter(c) || (c == '_');
    }

    public boolean isNumber(char c) {
        return isdigit(c) || (c == '.') || isLetter(c);
    }


    public boolean isSpecialNum(String str) {
        String hexFloat = "(0([xX])[0-9A-Fa-f]*\\.[0-9A-Fa-f]*(([pPeE])([+\\-])?[0-9A-Fa-f]*)?)|" +
                "(0([xX])[0-9A-Fa-f]*[.]?[0-9A-Fa-f]*([pPeE])(([+\\-])?[0-9A-Fa-f]*)?)";
        String hexInt = "0([xX])[0-9A-Fa-f]+";

        String sciNumber = "^[+-]?\\d*\\.?\\d+[Ee][+-]?$";


        return str.matches(hexFloat) || str.matches(hexInt) || str.matches(sciNumber);
    }


    public boolean isBlank(char c) {
        if (c == '\n') Token.lineNum++;
        return c == ' ' || c == '\r' || c == '\n' || c == '\t';
    }

}
