package mir;

import frontend.lexer.Token;
import frontend.lexer.TokenType;
import frontend.semantic.InitValue;
import frontend.syntaxChecker.Ast;

import java.util.HashMap;

public class GlobalVariable extends Constant {
    //constant fixed address (after linking).
    //对应 llvm 类型系统的 global variable
    //拥有@的全局标识符
    public Ast.Ident ident;
    public Constant value;

    // llvm 要求对于未初始化值需要一个指定的初值， 涉及到类型问题
    // 该表维护每种类型的初值只被产生一次
    // todo: 重构
    public static final HashMap<Type, GlobalVariable> undefTable = new HashMap<>();

    public static Constant getUndef(Type type) {
        if (type.isInt32Ty()) {
            return new Constant.ConstantInt(0);
        }
        if (type.isFloatTy()) {
            return new Constant.ConstantFloat(0);
        }
        if (undefTable.containsKey(type)) {
            return undefTable.get(type);
        }
        GlobalVariable undef = new GlobalVariable(type);
        undefTable.put(type, undef);
        return undef;
    }

    private GlobalVariable(Type type) {
        //全局变量的类型是指针类型
        super(new Type.PointerType(type));
        ident = new Ast.Ident(new Token(TokenType.IDENTIFIER, "undef_" + undefTable.size()));
        if (type.isInt32Ty()) {
            value = new Constant.ConstantInt(0);
        }
        else if (type.isFloatTy()) {
            value = new Constant.ConstantFloat(0);
        }
        else if (type.isPointerTy()) {
            // 数组初始化情况
            value = new Constant.ConstantPointerNull(this.type);
        }
        else {
            throw new RuntimeException("Unsupported type for undef");
        }
    }

    public GlobalVariable(Type innerType, Ast.Ident ident, InitValue initValue) {
        super(new Type.PointerType(innerType));
        this.ident = ident;
        this.value = initValue.genConstant();
    }

    public Type getInnerType() {
        Type.PointerType pointerType = (Type.PointerType) getType();
        return pointerType.getInnerType();
    }

    /**
     * 获取全局变量的Constant量类型为 Constant
     *
     * @return value
     */
    @Override
    public Constant getConstValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        return ident.equals(((GlobalVariable) o).ident);
    }

    /***
     * todo: 和前端确定用法，确定对数组情况的处理
     * @return value 展平后是否为零
     */
    @Override
    public boolean isZero() {
        return value.isZero();
    }

    @Override
    public String getDescriptor() {
        return "@" + ident.identifier.content;
    }

//    public ArrayList<Value> getInitValue() {
////        return getConstValue().flatten();
//        ArrayList<Value> flatten = new ArrayList<>();
//        //特判数组类型
//        if (value instanceof Constant.ConstantArray) {
//            return ((Constant.ConstantArray) value).flatten();
//        }
//        else {
//            flatten.add(value);
//        }
//        return flatten;
//    }

    // todo:
    @Override
    public String toString() {
        return getDescriptor() + " = global " + value.getType() + " " + value.toString();
    }

    /***
     * 获取ident 加上全局前缀
     * 用于输出量
     * @return riscv全局变量名
     */
    public String getRiscGlobalVariableName() {
        return "g_" + ident.toString();
    }
}
