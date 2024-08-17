package frontend.syntaxChecker;

import frontend.lexer.Token;
import frontend.lexer.TokenArray;
import frontend.exception.*;
import frontend.lexer.TokenType;
import midend.Util.FuncInfo;

import java.util.ArrayList;

/**
 * 递归下降
 */
public class Parser {
    private final TokenArray tokenArray;

    public Parser(TokenArray tokenArray) {
        this.tokenArray = tokenArray;
    }

    public Ast parseAst() throws SyntaxError {
        ArrayList<Ast.CompUnit> compUnits = new ArrayList<>();
        while (!tokenArray.isEnd()) {
            compUnits.add(parseDecl());
        }
        return new Ast(compUnits);
    }

    private Ast.Decl parseDecl() throws SyntaxError {
        if (tokenArray.check(2, TokenType.L_PAREN)) {
            return parseFuncDef();
        }
        if (tokenArray.check(TokenType.CONST)) {
            return parseConstDecl();
        }
        else {
            return parseVarDecl();
        }

    }

    private Ast.FuncDef parseFuncDef() throws SyntaxError {
        Token funcType = tokenArray.consumeToken(TokenType.INT, TokenType.FLOAT, TokenType.VOID);
        Ast.Ident ident = parseIdent();
        tokenArray.consumeToken(TokenType.L_PAREN);
        boolean hasFuncFParams = !tokenArray.checkAndSkip(TokenType.R_PAREN);
        if (hasFuncFParams) {
            ArrayList<Ast.FuncFParam> funcFParams = parseFuncFParams();
            tokenArray.consumeToken(TokenType.R_PAREN);
            Ast.Block block = parseBlock();
            return new Ast.FuncDef(funcType, ident, funcFParams, block);
        }
        Ast.Block block = parseBlock();
        return new Ast.FuncDef(funcType, ident, block);

    }

    private Ast.ConstDecl parseConstDecl() throws SyntaxError {
        tokenArray.consumeToken(TokenType.CONST);
        Ast.Btype btype = parseBtype();
        ArrayList<Ast.ConstDef> constDefs = new ArrayList<>();
        do {
            Ast.ConstDef constDef = parseConstDef();
            constDefs.add(constDef);
        } while (tokenArray.checkAndSkip(TokenType.COMMA));
        tokenArray.consumeToken(TokenType.SEMI);
        return new Ast.ConstDecl(btype, constDefs);
    }

    private Ast.ConstDef parseConstDef() throws SyntaxError {
        Ast.Ident ident = parseIdent();
        if (tokenArray.checkAndSkip(TokenType.L_BRACK)) {
            ArrayList<Ast.AddExp> addExps = new ArrayList<>();
            do {
                Ast.AddExp addExp = parseAddExp();
                addExps.add(addExp);
                tokenArray.consumeToken(TokenType.R_BRACK);
            } while (tokenArray.checkAndSkip(TokenType.L_BRACK));
            tokenArray.consumeToken(TokenType.ASSIGN);
            Ast.ConstInitVal constInitVal = parseConstInitVal();
            return new Ast.ConstDef(ident, addExps, constInitVal);
        }
        else {
            tokenArray.consumeToken(TokenType.ASSIGN);
            Ast.ConstInitVal constInitVal = parseConstInitVal();
            return new Ast.ConstDef(ident, constInitVal);
        }
    }

    private Ast.AddExp parseConstExp() throws SyntaxError {
        return parseAddExp();
    }

    private Ast.ConstInitVal parseConstInitVal() throws SyntaxError {
        if (tokenArray.checkAndSkip(TokenType.L_BRACE)) {
            if (tokenArray.checkAndSkip(TokenType.R_BRACE)) {
                return new Ast.ConstInitVal();
            }
            else {
                ArrayList<Ast.ConstInitVal> constInitVals = new ArrayList<>();
                do {
                    Ast.ConstInitVal constInitVal = parseConstInitVal();
                    constInitVals.add(constInitVal);
                } while (tokenArray.checkAndSkip(TokenType.COMMA));
                tokenArray.consumeToken(TokenType.R_BRACE);
                return new Ast.ConstInitVal(constInitVals);
            }
        }
        else {
            Ast.AddExp constExp = parseConstExp();
            return new Ast.ConstInitVal(constExp);
        }
    }

    private Ast.VarDecl parseVarDecl() throws SyntaxError {
        Ast.Btype btype = parseBtype();
        ArrayList<Ast.VarDef> varDefs = new ArrayList<>();
        do {
            Ast.VarDef varDef = parseVarDef();
            varDefs.add(varDef);
        } while (tokenArray.checkAndSkip(TokenType.COMMA));
        tokenArray.consumeToken(TokenType.SEMI);
        return new Ast.VarDecl(btype, varDefs);
    }

    private Ast.VarDef parseVarDef() throws SyntaxError {
        Ast.Ident ident = parseIdent();
        ArrayList<Ast.VarSuffix> varSuffixes = new ArrayList<>();
        ArrayList<Ast.AddExp> addExps = new ArrayList<>();
        if (tokenArray.check(TokenType.L_BRACK)) {
            do {
                Ast.VarSuffix varSuffix = parseVarSuffix();
                varSuffixes.add(varSuffix);
            } while (tokenArray.check(TokenType.L_BRACK));
            for (Ast.VarSuffix varSuffix :
                    varSuffixes) {
                addExps.add(varSuffix.getExp());
            }
            if (tokenArray.checkAndSkip(TokenType.ASSIGN)) {
                Ast.VarInitVal initVal = parseInitVal();
                return new Ast.VarDef(ident, addExps, initVal);
            }
            return new Ast.VarDef(ident, addExps);
        }
        else if (tokenArray.checkAndSkip(TokenType.ASSIGN)) {
            Ast.VarInitVal initVal = parseInitVal();
            return new Ast.VarDef(ident, initVal);
        }
        else {
            return new Ast.VarDef(ident);
        }
    }

    private Ast.VarInitVal parseInitVal() throws SyntaxError {
        if (tokenArray.checkAndSkip(TokenType.L_BRACE)) {
            if (tokenArray.checkAndSkip(TokenType.R_BRACE)) {
                return new Ast.VarInitVal();
            }
            else {
                ArrayList<Ast.VarInitVal> initVals = new ArrayList<>();
                do {
                    Ast.VarInitVal initVal = parseInitVal();
                    initVals.add(initVal);
                } while (tokenArray.checkAndSkip(TokenType.COMMA));
                tokenArray.consumeToken(TokenType.R_BRACE);
                return new Ast.VarInitVal(initVals);
            }
        }
        else {
            Ast.AddExp exp = parseConstExp();
            return new Ast.VarInitVal(exp);
        }
    }

    private Ast.Block parseBlock() throws SyntaxError {
        tokenArray.consumeToken(TokenType.L_BRACE);
        ArrayList<Ast.BlockItem> blockItems = new ArrayList<>();
        while (!tokenArray.checkAndSkip(TokenType.R_BRACE)) {
            Ast.BlockItem blockItem = parseBlockItem();
            blockItems.add(blockItem);
        }
        return new Ast.Block(blockItems);
    }

    private Ast.BlockItem parseBlockItem() throws SyntaxError {
        if (tokenArray.check(TokenType.CONST, TokenType.INT, TokenType.FLOAT)) {
            return parseDecl();
        }
        else {
            return parseStmt();
        }
    }

    private Ast.Lval exactLval(Ast.AddExp exp) throws SyntaxError {
        Ast.Lval lval;
        try {
            lval = exp.getMulExp().getUnaryExp().getPrimaryExp().getLval();
            assert lval != null;
        } catch (NullPointerException e) {
            throw new SyntaxError("Expected Lval to assign");
        }
        return lval;
    }

    /**
     * Stmt → LVal ‘=’ Exp ‘;’ | [Exp] ‘;’ | Block | ‘if’ ‘(’ Cond ‘)’ Stmt [ ‘else’ Stmt ] |
     * ‘while’ ‘(’ Cond ‘)’ Stmt | ‘break’ ‘;’ | ‘continue’ ‘;’ | ‘return’ [Exp] ‘;’
     */
    private Ast.Stmt parseStmt() throws SyntaxError {
        Token firstToken = tokenArray.ahead(0);
        switch (firstToken.tokenType) {
            case IDENTIFIER -> {
                if (tokenArray.ahead(1).tokenType == TokenType.L_PAREN) {
                    Ast.AddExp exp = parseAddExp();
                    tokenArray.consumeToken(TokenType.SEMI);
                    return new Ast.ExpStmt(exp);
                }
                Ast.AddExp exp = parseAddExp();
                if (!tokenArray.check(TokenType.ASSIGN)) {
                    return new Ast.ExpStmt(exp);
                }
                Ast.Lval lval = exactLval(exp);
                tokenArray.consumeToken(TokenType.ASSIGN);
                Ast.AddExp rExp = parseAddExp();
                tokenArray.consumeToken(TokenType.SEMI);
                return new Ast.AssignStmt(lval, rExp);
            }
            case SEMI -> {
                tokenArray.consumeToken(TokenType.SEMI);
                return new Ast.VoidStmt();
            }
            case L_BRACE -> {
                Ast.Block block = parseBlock();
                return new Ast.BlockStmt(block);
            }
            case IF -> {
                tokenArray.consumeToken(TokenType.IF);
                tokenArray.consumeToken(TokenType.L_PAREN);
                Ast.Cond cond = parseCond();
                tokenArray.consumeToken(TokenType.R_PAREN);
                Ast.Stmt stmt = parseStmt();
                if (tokenArray.checkAndSkip(TokenType.ELSE)) {
                    Ast.Stmt stmt1 = parseStmt();
                    return new Ast.IfElStmt(cond, stmt, stmt1);
                }
                return new Ast.IfStmt(cond, stmt);
            }
            case WHILE -> {
                tokenArray.consumeToken(TokenType.WHILE);
                tokenArray.consumeToken(TokenType.L_PAREN);
                Ast.Cond cond = parseCond();
                tokenArray.consumeToken(TokenType.R_PAREN);
                Ast.Stmt stmt = parseStmt();
                return new Ast.WhileStmt(cond, stmt);
            }
            case BREAK -> {
                tokenArray.consumeToken(TokenType.BREAK);
                tokenArray.consumeToken(TokenType.SEMI);
                return new Ast.BreakStmt();
            }
            case CONTINUE -> {
                tokenArray.consumeToken(TokenType.CONTINUE);
                tokenArray.consumeToken(TokenType.SEMI);
                return new Ast.ContinueStmt();
            }
            case RETURN -> {
                tokenArray.consumeToken(TokenType.RETURN);
                if (tokenArray.checkAndSkip(TokenType.SEMI)) {
                    return new Ast.ReturnStmt();
                }
                Ast.AddExp exp = parseAddExp();
                tokenArray.consumeToken(TokenType.SEMI);
                return new Ast.ReturnStmt(exp);
            }
            default -> {
                Ast.AddExp exp = parseAddExp();
                tokenArray.consumeToken(TokenType.SEMI);
                return new Ast.ExpStmt(exp);
            }
        }
    }

    private Ast.Cond parseCond() throws SyntaxError {
        return parseLOrExp();
    }

    private Ast.LOrExp parseLOrExp() throws SyntaxError {
        ArrayList<Ast.LAndExp> lAndExps = new ArrayList<>();
        do {
            Ast.LAndExp lAndExp = parseLAndExp();
            lAndExps.add(lAndExp);
        } while (tokenArray.checkAndSkip(TokenType.LOR));
        return new Ast.LOrExp(lAndExps);
    }

    private Ast.LAndExp parseLAndExp() throws SyntaxError {
        ArrayList<Ast.EqExp> eqExps = new ArrayList<>();
        do {
            Ast.EqExp eqExp = parseEqExp();
            eqExps.add(eqExp);
        } while (tokenArray.checkAndSkip(TokenType.LAND));
        return new Ast.LAndExp(eqExps);
    }

    private Ast.EqExp parseEqExp() throws SyntaxError {
        ArrayList<Ast.RelExp> relExps = new ArrayList<>();
        ArrayList<Token> eqOps = new ArrayList<>();
        do {
            Ast.RelExp relExp = parseRelExp();
            relExps.add(relExp);
            if (tokenArray.check(TokenType.EQ, TokenType.NE)) {
                Token eqOp = tokenArray.getToken();
                eqOps.add(eqOp);
            }
        } while (tokenArray.checkAndSkip(TokenType.EQ, TokenType.NE));
        return new Ast.EqExp(relExps, eqOps);
    }

    private Ast.RelExp parseRelExp() throws SyntaxError {
        ArrayList<Ast.AddExp> addExps = new ArrayList<>();
        ArrayList<Token> relOps = new ArrayList<>();
        do {
            Ast.AddExp addExp = parseAddExp();
            addExps.add(addExp);
            if (tokenArray.check(TokenType.LE, TokenType.GE, TokenType.LT, TokenType.GT)) {
                Token relOp = tokenArray.getToken();
                relOps.add(relOp);
            }
        } while (tokenArray.checkAndSkip(TokenType.LE, TokenType.GE, TokenType.LT, TokenType.GT));
        return new Ast.RelExp(addExps, relOps);
    }


    private ArrayList<Ast.FuncFParam> parseFuncFParams() throws SyntaxError {
        ArrayList<Ast.FuncFParam> funcFParams = new ArrayList<>();
        do {
            Ast.FuncFParam funcFParam = parseFuncFParam();
            funcFParams.add(funcFParam);
        } while (tokenArray.checkAndSkip(TokenType.COMMA));
        return funcFParams;
    }

    private Ast.Btype parseBtype() throws SyntaxError {
        Token type = tokenArray.consumeToken(TokenType.INT, TokenType.FLOAT);
        return new Ast.Btype(type);
    }

    private Ast.Ident parseIdent() throws SyntaxError {
        Token ident = tokenArray.consumeToken(TokenType.IDENTIFIER);
        return new Ast.Ident(ident);
    }

    private Ast.FuncFParam parseFuncFParam() throws SyntaxError {
        Ast.Btype btype = parseBtype();
        Ast.Ident ident = parseIdent();
        ArrayList<Ast.VarSuffix> varSuffixes = new ArrayList<>();
        if (tokenArray.check(TokenType.L_BRACK) && tokenArray.check(1, TokenType.R_BRACK)) {
            tokenArray.consumeToken(TokenType.L_BRACK);
            tokenArray.consumeToken(TokenType.R_BRACK);
            varSuffixes.add(new Ast.VarSuffix(true));
        }

        while (!(tokenArray.check(TokenType.R_PAREN) || tokenArray.check(TokenType.COMMA))) {
            Ast.VarSuffix varSuffix = parseVarSuffix();
            varSuffixes.add(varSuffix);
        }
        return new Ast.FuncFParam(btype, ident, varSuffixes);
    }

    private Ast.VarSuffix parseVarSuffix() throws SyntaxError {
        if (tokenArray.checkAndSkip(TokenType.L_BRACK)) {
            Ast.AddExp exp = parseAddExp();
            tokenArray.consumeToken(TokenType.R_BRACK);
            return new Ast.VarSuffix(exp);
        }
        else {
            return new Ast.VarSuffix();
        }
    }

    private Ast.AddExp parseAddExp() throws SyntaxError {
        Ast.MulExp mulExp = parseMulExp();
        Ast.AddExpSuffix addExpSuffix = parseAddExpSuffix();
        return new Ast.AddExp(mulExp, addExpSuffix);
    }

    private Ast.MulExp parseMulExp() throws SyntaxError {
        Ast.UnaryExp unaryExp = parseUnaryExp();
        Ast.MulExpSuffix mulExpSuffix = parseMulExpSuffix();
        return new Ast.MulExp(unaryExp, mulExpSuffix);
    }

    private Ast.AddExpSuffix parseAddExpSuffix() throws SyntaxError {
        if (tokenArray.check(TokenType.ADD)) {
            Token addOp = tokenArray.consumeToken(TokenType.ADD);
            Ast.MulExp mulExp = parseMulExp();
            Ast.AddExpSuffix addExpSuffix = parseAddExpSuffix();
            return new Ast.AddExpSuffix(mulExp, addExpSuffix, addOp);
        }
        else if (tokenArray.check(TokenType.SUB)) {
            Token subOp = tokenArray.consumeToken(TokenType.SUB);
            Ast.MulExp mulExp = parseMulExp();
            Ast.AddExpSuffix addExpSuffix = parseAddExpSuffix();
            return new Ast.AddExpSuffix(mulExp, addExpSuffix, subOp);
        }
        else {
            return new Ast.AddExpSuffix();
        }
    }

    private Ast.UnaryExp parseUnaryExp() throws SyntaxError {
        if (tokenArray.check(TokenType.IDENTIFIER) && tokenArray.check(1, TokenType.L_PAREN)) {
            Ast.Ident ident = parseIdent();
            tokenArray.consumeToken(TokenType.L_PAREN);
            if (tokenArray.checkAndSkip(TokenType.R_PAREN)) {
                return new Ast.UnaryExp(ident);
            }
            if (tokenArray.check(TokenType.STR)) {
                if (!ident.identifier.content.equals(FuncInfo.ExternFunc.PUTF.getName())) {
                    throw new SyntaxError("Unexpeted string");
                }
                Token str = tokenArray.consumeToken(TokenType.STR);
                if (tokenArray.checkAndSkip(TokenType.COMMA)) {
                    Ast.FuncRParams funcRParams = parseFuncRParams();
                    tokenArray.consumeToken(TokenType.R_PAREN);
                    return new Ast.UnaryExp(ident, funcRParams, str);
                }
                tokenArray.consumeToken(TokenType.R_PAREN);
                return new Ast.UnaryExp(ident, str);
            }
            Ast.FuncRParams funcRParams = parseFuncRParams();
            tokenArray.consumeToken(TokenType.R_PAREN);
            return new Ast.UnaryExp(ident, funcRParams);
        }
        else if (tokenArray.check(TokenType.NOT, TokenType.ADD, TokenType.SUB)) {
            Token unaryOp = tokenArray.consumeToken(TokenType.NOT, TokenType.ADD, TokenType.SUB);
            Ast.UnaryExp unaryExp = parseUnaryExp();
            return new Ast.UnaryExp(unaryOp, unaryExp);
        }
        else {
            return new Ast.UnaryExp(parsePrimaryExp());
        }
    }

    private Ast.MulExpSuffix parseMulExpSuffix() throws SyntaxError {
        if (tokenArray.check(TokenType.MUL, TokenType.DIV, TokenType.MOD)) {
            Token mulOp = tokenArray.consumeToken(TokenType.MUL, TokenType.DIV, TokenType.MOD);
            Ast.UnaryExp unaryExp = parseUnaryExp();
            Ast.MulExpSuffix mulExpSuffix = parseMulExpSuffix();
            return new Ast.MulExpSuffix(unaryExp, mulExpSuffix, mulOp);
        }
        else {
            return new Ast.MulExpSuffix();
        }
    }

    private Ast.PrimaryExp parsePrimaryExp() throws SyntaxError {
        if (tokenArray.checkAndSkip(TokenType.L_PAREN)) {
            Ast.AddExp exp = parseAddExp();
            tokenArray.consumeToken(TokenType.R_PAREN);
            return new Ast.PrimaryExp(exp);
        }
        else if (tokenArray.check(TokenType.IDENTIFIER)) {
            return new Ast.PrimaryExp(parseLval());
        }
        else {
            Token number = tokenArray.consumeToken(TokenType.DEC_INT, TokenType.OCT_INT, TokenType.HEX_INT, TokenType.DEC_FLOAT, TokenType.HEX_FLOAT);
            return new Ast.PrimaryExp(number);
        }
    }

    private Ast.FuncRParams parseFuncRParams() throws SyntaxError {
        ArrayList<Ast.AddExp> funcRparams = new ArrayList<>();
        do {
            Ast.AddExp addExp = parseAddExp();
            funcRparams.add(addExp);
        } while (tokenArray.checkAndSkip(TokenType.COMMA));
        return new Ast.FuncRParams(funcRparams);
    }

    private Ast.Lval parseLval() throws SyntaxError {
        Ast.Ident ident = parseIdent();
        Ast.LvalSuffix lvalSuffix = parseLvalSuffix();
        return new Ast.Lval(ident, lvalSuffix);
    }

    private Ast.LvalSuffix parseLvalSuffix() throws SyntaxError {
        if (tokenArray.checkAndSkip(TokenType.L_BRACK)) {
            if (tokenArray.checkAndSkip(TokenType.R_BRACK)) {
                Ast.LvalSuffix lvalSuffix = parseLvalSuffix();
                return new Ast.LvalSuffix(lvalSuffix);
            }
            Ast.AddExp exp = parseAddExp();
            tokenArray.consumeToken(TokenType.R_BRACK);
            Ast.LvalSuffix lvalSuffix = parseLvalSuffix();
            return new Ast.LvalSuffix(exp, lvalSuffix);
        }
        else {
            return new Ast.LvalSuffix();
        }
    }

}
