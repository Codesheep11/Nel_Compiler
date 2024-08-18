package frontend.lexer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class StringHandler {
    private final BufferedInputStream src;
    private final CharType type = new CharType();
    public StringHandler(BufferedInputStream src){
        this.src = src;
    }

    private int getchar() throws IOException {
        return src.read();
    }

    public boolean reachEOF() throws IOException {
        return (src.available() <= 0);
    }

//    public String scanf(int c) throws IOException {
//        StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append(c);
//        return getString(stringBuilder);
//    }
//
//    public String scanf(String str) throws IOException {
//        StringBuilder stringBuilder = new StringBuilder(str);
//        return getString(stringBuilder);
//    }

    /**
     * the function will skip all the blank characters and comments
     * @return string read from src
     */
    public String scanf() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        return getString(stringBuilder);
    }

    private String getString(StringBuilder stringBuilder) throws IOException {
        char cur = ' ';
        int tmp;
        while (type.isBlank(cur)) {
            tmp = getchar();
            if (tmp == -1) {
                return stringBuilder.toString();
            }
            cur = (char) tmp;
        }
        do {
            if(cur == '/') {
                tmp = getchar();
                switch (tmp) {
                    case -1 -> {
                        stringBuilder.append(cur);
                        return stringBuilder.toString();
                    }
                    case '/' -> {
                        int oneLineComments = getchar();
                        while (oneLineComments != '\n') {
                            if(oneLineComments == -1) {
                                return stringBuilder.toString();
                            }
                            oneLineComments = getchar();
                        }
                        return stringBuilder.toString();
                    }
                    case '*' -> {
                        int multiLineComments1 = getchar();
                        if (multiLineComments1 < 0) {
                            return stringBuilder.toString();
                        }
                        int multiLineComments0 = multiLineComments1;
                        multiLineComments1 = getchar();
                        if (multiLineComments1 < 0) {
                            return stringBuilder.toString();
                        }
                        while(!(multiLineComments0 == '*' && multiLineComments1 == '/')) {
                            multiLineComments0 = multiLineComments1;
                            multiLineComments1 = getchar();
                            if (multiLineComments1 < 0) {
                                return stringBuilder.toString();
                            }
                        }
                        return stringBuilder.toString();
                    }
                    default -> stringBuilder.append(cur);
                }
            } else if (cur == '"') {
                stringBuilder.append(cur);
                tmp = getchar();
                if(tmp == -1) {
                    return stringBuilder.toString();
                }
                while (tmp != '"') {
                    cur = (char) tmp;
                    stringBuilder.append(cur);
                    tmp = getchar();
                }
                cur = (char) tmp;
                stringBuilder.append(cur);
                tmp = getchar();
                if(tmp == -1) {
                    return stringBuilder.toString();
                }
            } else {
                stringBuilder.append(cur);
                tmp = getchar();
                if(tmp == -1) {
                    return stringBuilder.toString();
                }
            }
            cur = (char) tmp;
        } while (!type.isBlank(cur));
        return stringBuilder.toString();
    }

    public ArrayList<String> SplitString(String str) {
        ArrayList<String> subStrings = new ArrayList<>();
        ArrayList<StringBuilder> builders = new ArrayList<>();
        StringBuilder builder = null;
        int len = str.length();
        char last = 0;
        boolean isStr = false;
        for(int i = 0;i < len;i ++)
        {
            char cur = str.charAt(i);
            switch (cur) {
                case '(' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("(");
                }
                case ')' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last) || last == '.') {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add(")");
                }
                case '{' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("{");
                }
                case '}' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("}");
                }
                case ';' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add(";");
                }
                case '+' -> {
                    if (builder == null) {
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    if (isStr || type.isSpecialNum(builder.toString())) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("+");
                }
                case '-' -> {
                    if (builder == null) {
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    if (isStr || type.isSpecialNum(builder.toString())) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("-");
                }
                case '*' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("*");
                }
                case '/' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("/");
                }
                case '%' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("%");
                }
                case '!' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("!");
                }
                case ',' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last) || type.isNumber(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add(",");
                }
                case '[' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("[");
                }
                case '>' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add(">");
                }
                case '<' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("<");
                }
                case ']' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    subStrings.add("]");
                }
                case '=' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    if(last == '!')
                        subStrings.set(subStrings.size() - 1, "!=");
                    else if(last == '=')
                        subStrings.set(subStrings.size() - 1, "==");
                    else if(last == '<')
                        subStrings.set(subStrings.size() - 1, "<=");
                    else if(last == '>')
                        subStrings.set(subStrings.size() - 1, ">=");
                    else
                        subStrings.add("=");
                }
                case '|' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    if(last == '|')
                        subStrings.set(subStrings.size() - 1, "||");
                    else
                        subStrings.add("|");
                }
                case '&' -> {
                    if (isStr) {
                        builder.append(cur);
                        break;
                    }
                    if(type.isIdent(last)) {
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                    }
                    if(last == '&')
                        subStrings.set(subStrings.size() - 1, "&&");
                    else
                        subStrings.add("&");
                }
                case '"' -> {
                    if (builder == null) {
                        builders.add(new StringBuilder());
                        builder = builders.get(builders.size() - 1);
                    }
                    if (!isStr) {
                        isStr = true;
                        builder.append(cur);
                    } else {
                        builder.append(cur);
                        subStrings.add(builder.toString());
                        builder = new StringBuilder();
                        builders.add(builder);
                        isStr = false;
                    }
                }
                default -> {
                    if (builder == null) {
                        builders.add(new StringBuilder());
                        builder = builders.get(builders.size() - 1);
                    }
                    builder.append(cur);
                    if (i == len - 1) {
                        subStrings.add(builder.toString());
                    }
                }
            }
            last = cur;
        }
        return subStrings;
    }
}
