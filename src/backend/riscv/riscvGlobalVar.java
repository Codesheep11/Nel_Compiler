package backend.riscv;

import backend.operand.Operand;
import mir.Constant;
import mir.GlobalVariable;
import mir.Type;

import java.util.ArrayList;

/**
 * 用于存储全局变量的类:
 * 浮点变量，字符串变量，数组变量
 * 其中需要注意的是浮点数的局部初始化也都是需要开全局变量的
 */
// xxx: riscvGlobalVar 应该继承 Operand
public class riscvGlobalVar extends Operand {
    public String name;//即显示出来的label
    public GlobType type;

    public enum GlobType {
        STRING, FLOAT, INT
    }



    public riscvGlobalVar(String name, GlobType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * 根据输入生成对应的全局变量
     */
    public static riscvGlobalVar genGlobalVariable(GlobalVariable mirGlobalVar) {
        // 实际的全局变量类型
        Type mirType = mirGlobalVar.getInnerType();
        if (mirType.isFloatTy()) {
                float floatInit = (float) mirGlobalVar.getConstValue().getConstValue();
                return new riscvFloat(mirGlobalVar.getRiscGlobalVariableName(), floatInit);
        } else if (mirType.isInt32Ty()) {
                int intInit = (int) mirGlobalVar.getConstValue().getConstValue();
                return new riscvInt(mirGlobalVar.getRiscGlobalVariableName(), intInit);
        } else if (mirType.isArrayTy()) {
            // 计算字长
            int wordSize = mirType.queryBytesSizeOfType() / 4;
            if (mirGlobalVar.getConstValue() instanceof Constant.ConstantZeroInitializer) {
                if (((Type.ArrayType) mirType).getBasicEleType().isFloatTy()) {
                    return new riscvArray(mirGlobalVar.getRiscGlobalVariableName(), wordSize, GlobType.FLOAT);
                } else {
                    return new riscvArray(mirGlobalVar.getRiscGlobalVariableName(), wordSize, GlobType.INT);
                }
            } else {
                ArrayList<Constant> flattened = ((Constant.ConstantArray) mirGlobalVar.getConstValue()).getFlattenedArray();
                if (((Type.ArrayType) mirType).getBasicEleType().isFloatTy()) {
                    return new riscvArray(mirGlobalVar.getRiscGlobalVariableName(), wordSize, GlobType.FLOAT, flattened);
                } else {
                    return new riscvArray(mirGlobalVar.getRiscGlobalVariableName(), wordSize, GlobType.INT, flattened);
                }
            }
        }
        throw new RuntimeException("wrong global type:" + mirType);
    }
}
