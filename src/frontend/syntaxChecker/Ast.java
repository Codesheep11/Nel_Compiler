package frontend.syntaxChecker;

import frontend.lexer.Token;
import frontend.lexer.TokenType;

import java.util.ArrayList;

/**
 * 去除了左递归的SysY文法，new bing生成，不能保证正确性
 */
public class Ast {
    private final ArrayList<CompUnit> compUnitArray;

    /**
     * CompUnit → [ Decl ] { Decl }
     */
    public interface CompUnit {

    }

    /**
     * Decl → ConstDecl | VarDecl | FuncDef
     */
    public static class Decl implements CompUnit, BlockItem {
        private final Token type;

        public Decl(Token type) {
            assert type.tokenType == TokenType.INT || type.tokenType == TokenType.FLOAT ||  type.tokenType == TokenType.VOID;
            this.type = type;
        }

        public Token getType() {
            return type;
        }
    }

    /**
     * ConstDecl → ‘const’ BType ConstDef { ‘,’ ConstDef } ‘;’
     */
    public static class ConstDecl extends Decl {
        private final Btype btype;
        private final ArrayList<ConstDef> constDefs;

        public ConstDecl(Btype btype, ArrayList<ConstDef> constDefs) {
            super(btype.type);
            this.btype = btype;
            this.constDefs = constDefs;
        }

        public Btype getBtype() {
            return btype;
        }

        public ArrayList<ConstDef> getConstDefs() {
            return constDefs;
        }

    }

    /**
     * VarDecl → BType VarDef { ‘,’ VarDef } ‘;’
     */
    public static class VarDecl extends Decl {
        private final Btype btype;
        private final ArrayList<VarDef> varDefs;


        public VarDecl(Btype btype, ArrayList<VarDef> varDefs) {
            super(btype.type);
            this.btype = btype;
            this.varDefs = varDefs;
        }

        public Btype getBtype() {
            return btype;
        }

        public ArrayList<VarDef> getVarDefs() {
            return varDefs;
        }
    }

    /**
     * FuncDef → FuncType Ident ‘(’ [FuncFParams] ‘)’ Block
     */
    public static class FuncDef extends Decl {
        private final Ident ident;
        private ArrayList<FuncFParam> funcFParams = new ArrayList<>();
        private final Block block;
        public FuncDef(Token funcType, Ident ident, Block block) {
            super(funcType);
            this.ident = ident;
            this.block = block;
        }


        public FuncDef(Token funcType, Ident ident, ArrayList<FuncFParam> funcFParams, Block block) {
            super(funcType);
            this.ident = ident;
            this.funcFParams = funcFParams;
            this.block = block;
        }


        public Ident getIdent() {
            return ident;
        }

        public ArrayList<FuncFParam> getFuncFParams() {
            return funcFParams;
        }

        public Block getBlock() {
            return block;
        }
    }

    /**
     * Block → ‘{’ { BlockItem } ‘}’
     */
    public static class Block {

        private final ArrayList<BlockItem> blockItems;

        public Block(ArrayList<BlockItem> blockItems) {
            this.blockItems = blockItems;
        }

        public ArrayList<BlockItem> getBlockItems() {
            return blockItems;
        }
    }


    /**
     * BlockItem → Decl | Stmt
     */
    public interface BlockItem {}


    /**
     * Stmt → LVal ‘=’ Exp ‘;’ | [Exp] ‘;’ | Block | ‘if’ ‘(’ Cond ‘)’ Stmt [ ‘else’ Stmt ] |
     * ‘while’ ‘(’ Cond ‘)’ Stmt | ‘break’ ‘;’ | ‘continue’ ‘;’ | ‘return’ [Exp] ‘;’
     */
    public static class Stmt  implements BlockItem {}

    public static class AssignStmt extends Stmt {
        private final Lval lval;
        private final AddExp exp;

        public AssignStmt(Lval lval, AddExp exp) {
            this.lval = lval;
            this.exp = exp;
        }

        public AddExp getExp() {
            return exp;
        }

        public Lval getLval() {
            return lval;
        }
    }

    public static class ExpStmt extends Stmt {
        private final AddExp exp;

        public ExpStmt(AddExp exp) {
            this.exp = exp;
        }

        public AddExp getExp() {
            return exp;
        }
    }

    public static class BlockStmt extends Stmt {
        private final Block block;

        public BlockStmt(Block block) {
            this.block = block;
        }

        public Block getBlock() {
            return block;
        }
    }

    public static class IfStmt extends Stmt {
        private final Cond cond;
        private final Stmt stmt;

        public IfStmt(Cond cond, Stmt stmt) {
            this.cond = cond;
            this.stmt = stmt;
        }

        public Stmt getStmt() {
            return stmt;
        }

        public Cond getCond() {
            return cond;
        }

    }

    public static class WhileStmt extends Stmt {
        private final Cond cond;
        private final Stmt stmt;

        public WhileStmt(Cond cond, Stmt stmt) {
            this.cond = cond;
            this.stmt = stmt;
        }

        public Stmt getStmt() {
            return stmt;
        }

        public Cond getCond() {
            return cond;
        }

    }

    public static class IfElStmt extends Stmt {
        private final Cond cond;
        private final Stmt stmt;
        private final Stmt stmt1;

        public IfElStmt(Cond cond, Stmt stmt, Stmt stmt1) {
            this.cond = cond;
            this.stmt = stmt;
            this.stmt1 = stmt1;
        }

        public Cond getCond() {
            return cond;
        }

        public Stmt getStmt() {
            return stmt;
        }

        public Stmt getStmt1() {
            return stmt1;
        }

    }

    public static class VoidStmt extends Stmt {
        public VoidStmt() {}
    }

    public static class BreakStmt extends Stmt {
        public BreakStmt() {}
    }

    public static class ContinueStmt extends Stmt {
        public ContinueStmt() {}
    }

    public static class ReturnStmt extends Stmt {

        private AddExp exp;

        public ReturnStmt() {}

        public ReturnStmt(AddExp exp) {
            this.exp = exp;
        }

        public AddExp getExp() {
            return exp;
        }
    }

    /**
     * FuncFParam → BType Ident VarSuffix
     */
    public static class FuncFParam {
        private final Btype btype;
        private final Ident ident;
        private final ArrayList<VarSuffix> varSuffixes;

        public FuncFParam(Btype btype, Ident ident, ArrayList<VarSuffix> varSuffixes) {
            this.ident = ident;
            this.btype = btype;
            this.varSuffixes = varSuffixes;
        }

        public Ident getIdent() {
            return ident;
        }

        public Btype getBtype() {
            return btype;
        }

        public ArrayList<VarSuffix> getVarSuffixes() {
            return varSuffixes;
        }
    }


    /**
     * BType → ‘int’ | ‘float’
     */
    public static class Btype {
       public Token type;

       public Btype(Token type) {
           assert type.tokenType == TokenType.INT || type.tokenType == TokenType.FLOAT;
           this.type = type;
       }

    }

    /**
     * ConstDef → Ident { ‘[’ ConstExp ‘]’ } ‘=’ ConstInitVal
     */
    public static class ConstDef {
        private final Ident ident;
        private ArrayList<AddExp> constExps = new ArrayList<>();
        private final ConstInitVal constInitVal;

        public ConstDef(Ident ident, ConstInitVal constInitVal) {
            this.ident = ident;
            this.constInitVal = constInitVal;
        }

        public ConstDef(Ident ident, ArrayList<AddExp> constExps, ConstInitVal constInitVal) {
            this.ident = ident;
            this.constExps = constExps;
            this.constInitVal = constInitVal;
        }

        public Ident getIdent() {
            return ident;
        }

        public ArrayList<AddExp> getConstExps() {
            return constExps;
        }

        public ConstInitVal getConstInitVal() {
            return constInitVal;
        }

    }

    /**
     * VarDef → Ident { '[' ConstExp ']' }['=' InitVal]
     */
    public static class VarDef {
        private final Ident ident;

        private ArrayList<AddExp> addExps = new ArrayList<>();

        private final VarInitVal initVal;

        public VarDef(Ident ident, ArrayList<AddExp> addExps, VarInitVal initVal) {
            this.ident = ident;
            this.addExps = addExps;
            this.initVal = initVal;
        }

        public VarDef(Ident ident, ArrayList<AddExp> addExps) {
            this.ident = ident;
            this.addExps = addExps;
            this.initVal = new VarInitVal();
        }

        public VarDef(Ident ident, VarInitVal initVal) {
            this.ident = ident;
            this.initVal = initVal;
        }

        public VarDef(Ident ident) {
            this.ident = ident;
            this.initVal = new VarInitVal();
        }

        public Ident getIdent() {
            return ident;
        }

        public ArrayList<AddExp> getAddExps() {
            return addExps;
        }

        public VarInitVal getInitVal() {
            return initVal;
        }
    }

    public static class Ident {
        public Token identifier;

        public Ident(Token token) {
            assert token.tokenType == TokenType.IDENTIFIER;
            this.identifier = token;
        }

        @Override
        public String toString() {
            return identifier.content;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (getClass() != o.getClass()) {
                return false;
            }
            return identifier.content.equals(((Ident) o).identifier.content);
        }

    }

    /**
     * VarSuffix →  ['[' Exp ']' ]
     */
    public static class VarSuffix {
        private AddExp exp;
        private final boolean omitExp;

        public VarSuffix(AddExp exp) {
            this.exp = exp;
            this.omitExp = false;
        }

        public VarSuffix(boolean omitExp) {
            this.omitExp = omitExp;
        }

        public VarSuffix() {this.omitExp = false;}

        public AddExp getExp() {
            return exp;
        }

        public boolean isOmitExp() {
            return omitExp && (exp == null);
        }
    }

    /**
     * AddExp → MulExp AddExpSuffix
     */
    public static class AddExp {
        private final MulExp mulExp;
        private final AddExpSuffix addExpSuffix;

        public AddExp(MulExp mulExp, AddExpSuffix addExpSuffix) {
            this.mulExp = mulExp;
            this.addExpSuffix = addExpSuffix;
        }

        public MulExp getMulExp() {
            return mulExp;
        }

        public AddExpSuffix getAddExpSuffix() {
            return addExpSuffix;
        }


    }

    public static abstract class InitVal {
        public abstract boolean hasInitVal();
        public abstract AddExp getExp();
        public abstract  ArrayList<? extends InitVal> getInitVals();
        public int getFlattenSize() {
            if (!hasInitVal()) {
                return 0;
            }
            if (getExp() == null) {
                int count = 0;
                for (InitVal initVal:
                     getInitVals()) {
                    count += initVal.getFlattenSize();
                }
                return count;
            } else {
                return 1;
            }
        }

    }

    /**
     * InitVal → Exp | '{' [ InitVal { ',' InitVal } ] '}'
     */
    public static class VarInitVal extends InitVal {
        private AddExp addExp = null;
        private ArrayList<VarInitVal> initVals = null;

        public VarInitVal(AddExp Exp) {
            this.addExp = Exp;
        }

        public VarInitVal(ArrayList<VarInitVal> initVals) {
            this.initVals = initVals;
        }

        public VarInitVal() {}


        public AddExp getExp() {
            return addExp;
        }


        public ArrayList<VarInitVal> getInitVals() {
            return initVals;
        }


        public boolean hasInitVal() { return (addExp != null) || (initVals != null); }

    }

    /**
     * ConstInitVal → ConstExp | ‘{’ [ ConstInitVal { ‘,’ ConstInitVal } ] ‘}’
     */
    public static class ConstInitVal extends InitVal {
        private AddExp constExp = null;
        private ArrayList<ConstInitVal> constInitVals = null;

        public ConstInitVal(AddExp constExp) {
            this.constExp = constExp;
        }

        public ConstInitVal(ArrayList<ConstInitVal> constInitVals) {
            this.constInitVals = constInitVals;
        }

        public ConstInitVal() {}


        public AddExp getExp() {
            return constExp;
        }

        public ArrayList<ConstInitVal> getInitVals() {
            return constInitVals;
        }


        public boolean hasInitVal() {
            return (constExp != null) || (constInitVals != null);
        }
    }

    /**
     * MulExp → UnaryExp MulExpSuffix
     */
    public static class MulExp {
        private final UnaryExp unaryExp;
        private final MulExpSuffix mulExpSuffix;

        public MulExp(UnaryExp unaryExp, MulExpSuffix mulExpSuffix){
            this.mulExpSuffix = mulExpSuffix;
            this.unaryExp = unaryExp;
        }

        public UnaryExp getUnaryExp() {
            return unaryExp;
        }

        public MulExpSuffix getMulExpSuffix() {
            return mulExpSuffix;
        }

    }

    /**
     * AddExpSuffix → ‘+’ MulExp AddExpSuffix | ‘-’ MulExp AddExpSuffix | ε
     */
    public static class AddExpSuffix {
        private MulExp mulExp = null;
        private AddExpSuffix addExpSuffix = null;

        private Token addOp = null;

        public AddExpSuffix(MulExp mulExp, AddExpSuffix addExpSuffix, Token addOp) {
            assert addOp.tokenType == TokenType.ADD || addOp.tokenType == TokenType.SUB;
            this.mulExp = mulExp;
            this.addExpSuffix = addExpSuffix;
            this.addOp = addOp;
        }

        public AddExpSuffix() {}

        public AddExpSuffix getAddExpSuffix() {
            return addExpSuffix;
        }

        public MulExp getMulExp() {
            return mulExp;
        }

        public Token getAddOp() {
            return addOp;
        }

        public boolean hasMulExp() {
            return mulExp != null;
        }

        public boolean hasNext() {
            if (addExpSuffix == null) {
                return false;
            }
            if (addExpSuffix.mulExp == null) {
                return false;
            }
            return true;
        }
    }

    /**
     * UnaryExp → PrimaryExp | Ident ‘(’ [FuncRParams] ‘)’ | UnaryOp UnaryExp
     */
    public static class UnaryExp {
        private PrimaryExp primaryExp = null;
        private Ident ident = null;
        private FuncRParams funcRParams = new FuncRParams(new ArrayList<>());
        private Token unaryOp = null;
        private UnaryExp unaryExp = null;
        private Token STR = null;

        public UnaryExp(PrimaryExp primaryExp) {
            this.primaryExp = primaryExp;
        }

        public UnaryExp(Ident ident, FuncRParams funcRParams) {
            this.ident = ident;
            this.funcRParams = funcRParams;
        }

        public UnaryExp(Ident ident, FuncRParams funcRParams, Token str) {
            this.ident = ident;
            this.funcRParams = funcRParams;
            this.STR = str;
        }

        public UnaryExp(Ident ident) {
            this.ident = ident;
        }

        public UnaryExp(Ident ident, Token str) {
            this.ident = ident;
            this.STR = str;
        }

        public UnaryExp(Token unaryOp, UnaryExp unaryExp) {
            assert unaryOp.tokenType == TokenType.NOT || unaryOp.tokenType == TokenType.ADD || unaryOp.tokenType == TokenType.SUB;
            this.unaryOp = unaryOp;
            this.unaryExp = unaryExp;
        }

        public PrimaryExp getPrimaryExp() {
            return primaryExp;
        }

        public Ident getIdent() {
            return ident;
        }

        public FuncRParams getFuncRParams() {
            return funcRParams;
        }

        public Token getUnaryOp() {
            return unaryOp;
        }

        public UnaryExp getUnaryExp() {
            return unaryExp;
        }

        public boolean isPrimaryExp() {
            return primaryExp != null;
        }

        public boolean isFunctionCall() {
            return ident != null;
        }

        public boolean isSubUnaryExp() {
            return unaryOp != null && unaryExp != null;
        }

        public Token getSTR() {
            return STR;
        }
    }

    /**
     * MulExpSuffix → ‘*’ UnaryExp MulExpSuffix | ‘/’ UnaryExp MulExpSuffix | ‘%’ UnaryExp MulExpSuffix | ε
     */
    public static class MulExpSuffix {
        private UnaryExp unaryExp = null;
        private MulExpSuffix mulExpSuffix = null;
        private Token mulOp = null;

        public MulExpSuffix(UnaryExp unaryExp, MulExpSuffix mulExpSuffix, Token mulOp) {
            assert mulOp.tokenType == TokenType.MUL || mulOp.tokenType == TokenType.DIV || mulOp.tokenType == TokenType.MOD;
            this.mulExpSuffix = mulExpSuffix;
            this.unaryExp = unaryExp;
            this.mulOp = mulOp;
        }

        public MulExpSuffix() {}

        public Token getMulOp() {
            return mulOp;
        }

        public UnaryExp getUnaryExp() {
            return unaryExp;
        }

        public MulExpSuffix getMulExpSuffix() {
            return mulExpSuffix;
        }

        public boolean hasUnary() {
            return unaryExp != null;
        }

        public boolean hasNext() {
            if (mulExpSuffix == null) {
                return false;
            }
            if (mulExpSuffix.unaryExp == null) {
                return false;
            }
            return true;
        }
    }

    /**
     * PrimaryExp → ‘(’ Exp ‘)’ | LVal | Number
     * Exp → AddExp
     */
    public static class PrimaryExp {
        private AddExp exp = null;
        private Lval lval = null;
        private Token number = null;

        public PrimaryExp(AddExp exp) {
            this.exp = exp;
        }

        public PrimaryExp(Lval lval) {
            this.lval = lval;
        }

        public PrimaryExp(Token number) {
            this.number = number;
        }

        public AddExp getExp() {
            return exp;
        }

        public Lval getLval() {
            return lval;
        }

        public Token getNumber() {
            return number;
        }

        public boolean isExp() {
            return exp != null;
        }

        public boolean isLval() {
            return lval != null;
        }

        public boolean isNumber() {
            return number != null;
        }

    }

    /**
     * FuncRParams → Exp { ',' Exp }
     * Exp → AddExp
     */
    public static class FuncRParams {
        private final ArrayList<AddExp> params;

        public FuncRParams(ArrayList<AddExp> params) {
            this.params = params;
        }

        public ArrayList<AddExp> getParams() {
            return params;
        }
    }

    /**
     * LVal → Ident LValSuffix
     */
    public static class Lval {
        private final Ident ident;
        private final LvalSuffix lvalSuffix;

        public Lval(Ident ident, LvalSuffix lvalSuffix) {
            this.ident = ident;
            this.lvalSuffix = lvalSuffix;
        }

        public Ident getIdent() {
            return ident;
        }

        public LvalSuffix getLvalSuffix() {
            return lvalSuffix;
        }

        public ArrayList<AddExp> getExps() {
            ArrayList<AddExp> addExps = new ArrayList<>();
            LvalSuffix cur = lvalSuffix;
            do {
                if (cur.hasExp()) {
                    addExps.add(cur.getExp());
                }
                cur = cur.getLvalSuffix();
            } while(cur != null);
            return addExps;
        }
    }

    /**
     * LValSuffix → ‘[’ Exp ‘]’ LValSuffix | ε
     */
    public static class LvalSuffix {
        private AddExp exp = null;
        private LvalSuffix lvalSuffix = null;

        public LvalSuffix(AddExp exp, LvalSuffix lvalSuffix) {
            this.exp = exp;
            this.lvalSuffix = lvalSuffix;
        }

        public LvalSuffix(LvalSuffix lvalSuffix) {
            this.lvalSuffix = lvalSuffix;
        }

        public LvalSuffix() {}

        public LvalSuffix getLvalSuffix() {
            return lvalSuffix;
        }

        public AddExp getExp() {
            return exp;
        }

        public boolean hasExp() {
            return exp != null;
        }

        public boolean hasNext() {
            return lvalSuffix != null;
        }
    }

    /**
     * Cond -> LOrExp
     */
    public static class Cond {

    }

    /**
     * LOrExp → [{LAndExp LOrOp}] LAndExp
     */
    public static class LOrExp extends Cond {
        private final ArrayList<LAndExp> lAndExps;

        public LOrExp(ArrayList<LAndExp> lAndExps) {
            this.lAndExps = lAndExps;
        }

        public ArrayList<LAndExp> getlAndExps() {
            return lAndExps;
        }
    }

    /**
     * LAndExp → [{EqExp LAndOp}] EqExp
     */
    public static class LAndExp {
        private final ArrayList<EqExp> eqExps;

        public LAndExp(ArrayList<EqExp> eqExps) {
            this.eqExps = eqExps;
        }

        public ArrayList<EqExp> getEqExps() {
            return eqExps;
        }
    }

    /**
     * EqExp → [{RelExp EqOp}] RelExp
     */
    public static class EqExp {
        private final ArrayList<RelExp> relExps;

        private final ArrayList<Token> eqOps;

        public EqExp(ArrayList<RelExp> relExps, ArrayList<Token> eqOps) {
            this.relExps = relExps;
            this.eqOps = eqOps;
        }

        public ArrayList<RelExp> getRelExps() {
            return relExps;
        }

        public ArrayList<Token> getEqOps() {
            return eqOps;
        }
    }

    /**
     * RelExp → [{AddExp RelOp}] AddExp
     */
    public static class RelExp {
        private final ArrayList<AddExp> addExps;

        private final ArrayList<Token> relOps;

        public RelExp(ArrayList<AddExp> addExps, ArrayList<Token> relOps) {
            this.addExps = addExps;
            this.relOps = relOps;
        }

        public ArrayList<AddExp> getAddExps() {
            return addExps;
        }

        public ArrayList<Token> getRelOps() {
            return relOps;
        }
    }


    public Ast(ArrayList<CompUnit> compUnits) {
        assert compUnits != null;
        this.compUnitArray = compUnits;
    }

    public ArrayList<CompUnit> getUnits() {
        return this.compUnitArray;
    }

}
