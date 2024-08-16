package frontend.semantic;

import frontend.exception.*;
import frontend.lexer.Token;
import frontend.lexer.TokenType;
import frontend.syntaxChecker.Ast;
import mir.Constant;
import mir.Value;

import java.util.ArrayList;

/**
 * 常数计算器，用于编译期间求值
 */
public class Calculator {
    private final SymTable currentSymTable;


    public Calculator(SymTable symTable) {
        this.currentSymTable = symTable;
    }

    public int evalConsInt(Ast.AddExp exp) throws SemanticError {
        Object ret = evalConstExp(exp);
        return (int) ret;
    }

    public Object evalConstExp(Ast.AddExp exp) throws SemanticError {
        Object ret;
        Ast.MulExp mulExp = exp.getMulExp();
        Ast.AddExpSuffix addExpSuffix = exp.getAddExpSuffix();
        ret = evalMulExp(mulExp);
        while (addExpSuffix.hasMulExp()) {
            Object mulRet = evalMulExp(addExpSuffix.getMulExp());
            if (ret instanceof Float || mulRet instanceof Float) {
                float f1 = ret instanceof Integer ? (float) ((int) ret) : (float) ret;
                float f2 = mulRet instanceof Integer ? (float) ((int) mulRet) : (float) mulRet;
                switch (addExpSuffix.getAddOp().tokenType) {
                    case ADD -> ret = f1 * f2;
                    case SUB -> ret = f1 / f2;
                    default -> throw new SemanticError("Bad MulOp: "+addExpSuffix.getAddOp().tokenType.toString());
                }
            } else {
                if(!(ret instanceof Integer || mulRet instanceof Integer)) {
                    throw new SemanticError("Wrong Type of MulExp Number");
                }
                int i1 = (int) ret;
                int i2 = (int) mulRet;
                switch (addExpSuffix.getAddOp().tokenType) {
                    case ADD -> ret = i1 + i2;
                    case SUB -> ret = i1 - i2;
                    default -> throw new SemanticError("Bad MulOp: "+addExpSuffix.getAddOp().tokenType.toString());
                }
            }

            if (addExpSuffix.hasNext()) {
                addExpSuffix = addExpSuffix.getAddExpSuffix();
            } else {
                break;
            }
        }
        return ret;
    }

    private Object evalMulExp(Ast.MulExp mulExp) throws SemanticError {
        Object ret;
        Ast.UnaryExp unaryExp = mulExp.getUnaryExp();
        Ast.MulExpSuffix mulExpSuffix = mulExp.getMulExpSuffix();
        ret = evalUnaryExp(unaryExp);
        while (mulExpSuffix.hasUnary()) {
            Object unaryRet = evalUnaryExp(mulExpSuffix.getUnaryExp());
            if (ret instanceof Float || unaryRet instanceof Float) {
                float f1 = ret instanceof Integer ? (float) ((int) ret) : (float) ret;
                float f2 = unaryRet instanceof Integer ? (float) ((int) unaryRet) : (float) unaryRet;
                switch (mulExpSuffix.getMulOp().tokenType) {
                    case MUL -> ret = f1 * f2;
                    case DIV -> ret = f1 / f2;
                    case MOD -> ret = f1 % f2;
                    default -> throw new SemanticError("Bad MulOp: "+mulExpSuffix.getMulOp().tokenType.toString());
                }
            } else {
                if(!(ret instanceof Integer || unaryRet instanceof Integer)) {
                    throw new SemanticError("Wrong Type of MulExp Number");
                }
                int i1 = (int) ret;
                int i2 = (int) unaryRet;
                switch (mulExpSuffix.getMulOp().tokenType) {
                    case MUL -> ret = i1 * i2;
                    case DIV -> ret = i1 / i2;
                    case MOD -> ret = i1 % i2;
                    default -> throw new SemanticError("Bad MulOp: "+mulExpSuffix.getMulOp().tokenType.toString());
                }
            }

            if (mulExpSuffix.hasNext()) {
                mulExpSuffix = mulExpSuffix.getMulExpSuffix();
            } else {
                break;
            }
        }
        return ret;
    }

    private Object evalUnaryExp(Ast.UnaryExp unaryExp) throws SemanticError {
        Object ret;
        if(unaryExp.isPrimaryExp()) {
            ret = evalPrimaryExp(unaryExp.getPrimaryExp());
        } else if (unaryExp.isFunctionCall()) {
            throw new SemanticError("Invalid const exp");
        } else if (unaryExp.isSubUnaryExp()) {
            Token unaryOp = unaryExp.getUnaryOp();
            Ast.UnaryExp subUnaryExp = unaryExp.getUnaryExp();
            switch (unaryOp.tokenType) {
                case ADD -> ret = evalUnaryExp(subUnaryExp);
                case SUB -> {
                    ret = evalUnaryExp(subUnaryExp);
                    if (ret instanceof Integer) {
                        return - (int) ret;
                    }
                    if (ret instanceof Float) {
                        return - (float) ret;
                    }
                    throw new SemanticError("Wrong type of result");
                }
                case NOT -> {
                    ret = evalUnaryExp(subUnaryExp);
                    if (ret instanceof Integer) {
                        return (int) ret != 0 ? 0 : ret;
                    }
                    if (ret instanceof Float) {
                        return - (float) ret != 0 ? (float) 0 : ret;
                    }
                    throw new SemanticError("Wrong type of result");
                }
                default -> throw new SemanticError("Unsupported Unary Op"+unaryOp.tokenType);
            }
        } else {
            throw new SemanticError("Bad unary exp");
        }
        return ret;
    }

    private Object evalPrimaryExp(Ast.PrimaryExp primaryExp) throws SemanticError {
        if (primaryExp.isExp()) {
            return evalConstExp(primaryExp.getExp());
        } else if (primaryExp.isLval()) {
            return evalLval(primaryExp.getLval());
        } else if (primaryExp.isNumber()) {
            return evalNumber(primaryExp.getNumber());
        } else {
            throw new SemanticError("Bad Primary Exp");
        }
    }

    private  Object evalLval(Ast.Lval lval) throws SemanticError {
        Ast.Ident ident = lval.getIdent();
        if (!currentSymTable.hasSymbol(ident, true)) {
            throw new SemanticError("UnDefined variable: "+ident.identifier.content);
        }
        Symbol symbol = currentSymTable.getSymbol(ident, true);
        if (symbol.getValue() == null) {
            throw new SemanticError("Symbol not initialized");
        }
        InitValue initValue = symbol.getValue();

        Ast.LvalSuffix lvalSuffix = lval.getLvalSuffix();
        ArrayList<Integer> dims = new ArrayList<>();
        while (lvalSuffix.hasExp()) {
            Object dim = evalConstExp(lvalSuffix.getExp());
            if (!(dim instanceof Integer)) {
                throw new SemanticError("Dim of Array should be Integer");
            }
            if ((Integer) dim < 0) {
                throw new SemanticError("Dim of Array should be positive");
            }
            dims.add((Integer) dim);
            if (lvalSuffix.hasNext()) {
                lvalSuffix = lvalSuffix.getLvalSuffix();
            } else {
                break;
            }
        }
        for (Integer dim:
             dims) {
            if(!(initValue instanceof InitValue.ArrayInit)) {
                throw new SemanticError("Should be Array Type");
            }
            initValue = ((InitValue.ArrayInit) initValue).getValue(dim);
        }

        if (!(initValue instanceof InitValue.ValueInit)) {
            throw new SemanticError("Expected Value but got "+ initValue.getClass());
        }

        Value val = ((InitValue.ValueInit) initValue).getValue();
        assert val instanceof Constant;
        Object ret = ((Constant) val).getConstValue();
        assert ret != null;
        return ret;
    }

    private Object evalNumber(Token number) throws SemanticError {
        if (number.tokenType == TokenType.DEC_FLOAT || number.tokenType == TokenType.HEX_FLOAT) {
            return Float.parseFloat(number.content);
        } else if (number.tokenType == TokenType.DEC_INT || number.tokenType == TokenType.HEX_INT || number.tokenType == TokenType.OCT_INT) {
            if (number.tokenType == TokenType.HEX_INT) {
                if (number.content.contains("0x") || number.content.contains("0X")) {
                    return Integer.parseInt(number.content.substring(2).toUpperCase(), 16);
                } else {
                    return Integer.parseInt(number.content.toUpperCase(), 16);
                }
            }
            if (number.tokenType == TokenType.OCT_INT) {
                return Integer.parseInt(number.content, 8);
            }
            return Integer.parseInt(number.content);
        } else {
            throw new SemanticError("NAN: "+number.content);
        }
    }

}