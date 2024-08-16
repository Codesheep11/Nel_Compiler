package mir;

import frontend.semantic.InitValue;
import frontend.syntaxChecker.Ast;

import java.util.HashMap;

public class GlobalVariable extends Constant {
    //constant fixed address (after linking).
    //对应 llvm 类型系统的 global variable
    //拥有@的全局标识符
    public final String label;
    public Constant value;

    // llvm 要求对于未初始化值需要一个指定的初值， 涉及到类型问题
    // 该表维护每种类型的初值只被产生一次
    // todo: 重构
    public static final HashMap<Type, GlobalVariable> undefTable = new HashMap<>();

    public static Constant getUndef(Type type) {
        if (type.isInt32Ty()) {
            return Constant.ConstantInt.get(0);
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
        label = "undef_" + undefTable.size();
        if (type.isInt32Ty()) {
            value = Constant.ConstantInt.get(0);
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
        this.label = ident.toString();
        this.value = initValue.genConstant();
    }

    public GlobalVariable(Constant constant, String label) {
        super(new Type.PointerType(constant.type));
        this.label = label;
        this.value = constant;
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
        return label.equals(((GlobalVariable) o).label);
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
        return "@" + label;
    }

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
        return "g_" + label;
    }
}
