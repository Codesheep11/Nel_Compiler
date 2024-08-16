package backend.Ir2RiscV;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvFloat;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.La;
import backend.riscv.RiscvInstruction.Li;
import backend.riscv.RiscvInstruction.R2;
import midend.Analysis.AlignmentAnalysis;
import mir.*;

import java.util.HashMap;

public class VirRegMap {
    public static final VirRegMap VRM = new VirRegMap();

    public final HashMap<Value, Reg> map = new HashMap<>();

    // 被b指令用的寄存器的hashset表

    public static final HashMap<Reg, Integer> bUseReg = new HashMap<>();

    private Function nowFunction;

    public VirRegMap() {

    }

    public void clean(Function function) {
        this.nowFunction = function;
        map.clear();
    }

    public Reg genReg(Value value) {
        Type type = value.getType();
        Reg reg;
        if (type.isFloatTy()) {
            reg = Reg.getVirtualReg(Reg.RegType.FPR, 32);
        }
        else if (type.isInt64Ty() || type.isPointerTy() || type.equals(Type.FunctionType.FUNC_TYPE)) {
            reg = Reg.getVirtualReg(Reg.RegType.GPR, 64);
        }
        else if (type.isInt1Ty() || type.isInt32Ty()) {
            reg = Reg.getVirtualReg(Reg.RegType.GPR, 32);
        }
        else {
            throw new RuntimeException("alloc array to a reg");
        }
        return reg;
    }

    public void addValue(Value value) {
        Type type = value.getType();
        Reg reg;
        if (type.isFloatTy()) {
            reg = Reg.getVirtualReg(Reg.RegType.FPR, 32);
        }
        else if (type.isInt64Ty() || type.isPointerTy() || type.equals(Type.FunctionType.FUNC_TYPE)) {
            reg = Reg.getVirtualReg(Reg.RegType.GPR, 64);
        }
        else if (type.isInt1Ty() || type.isInt32Ty()) {
            reg = Reg.getVirtualReg(Reg.RegType.GPR, 32);
        }
        else {
            throw new RuntimeException("alloc " + value.getType() + " to a reg");
        }
        // bits:位数，64，32?
        map.put(value, reg);
    }

    // 不知道是否在前面的指令中被建立了,该方法是传入一个value，如果已经有了虚拟寄存器则返回，否则现场分配一个然后返回
    // 当然,需要保证如果是float或者int常量的话需要提前把其中数值加到寄存器中
    // 需要额外保证所有的存到栈中的寄存器arg均要在这个阶段被从地址中拿到值
    public Reg ensureRegForValue(Value value) {
        if (nowFunction.isAddressArg(value)) {
            addValue(value);
            Reg reg = map.get(value);
            Reg sp = Reg.getPreColoredReg(Reg.PhyReg.sp, 64);
            Address address = nowFunction.getArgAddress((Function.Argument) value);
            if (value.getType().isFloatTy()) {
                CodeGen.nowBlock.addInstLast(new LS(CodeGen.nowBlock, reg, sp, address, LS.LSType.flw, AlignmentAnalysis.AlignType.ALIGN_BYTE_8));
            }
            else if (value.getType().isInt64Ty() || value.getType().isPointerTy()) {
                CodeGen.nowBlock.addInstLast(new LS(CodeGen.nowBlock, reg, sp, address, LS.LSType.ld, AlignmentAnalysis.AlignType.ALIGN_BYTE_8));
            }
            else if (value.getType().isInt32Ty()) {
                CodeGen.nowBlock.addInstLast(new LS(CodeGen.nowBlock, reg, sp, address, LS.LSType.lw, AlignmentAnalysis.AlignType.ALIGN_BYTE_8));
            }
            else {
                throw new RuntimeException("wrong type");
            }
            return reg;
        }
        else if (value instanceof Constant) {
            if ((value instanceof Constant.ConstantInt && (Integer) ((Constant.ConstantInt) value).getConstValue() == 0)
                    || (value instanceof Constant.ConstantBool) && (Integer) ((Constant.ConstantBool) value).getConstValue() == 0)
            {
                return Reg.getPreColoredReg(Reg.PhyReg.zero, 32);
            }
            Reg reg = genReg(value);
            if (value instanceof Constant.ConstantFloat) {
                Float init = ((Float) ((Constant.ConstantFloat) value).getConstValue());
                RiscvFloat rf = CodeGen.ansRis.getSameFloat(init);
                Reg tmp = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                CodeGen.nowBlock.addInstLast(new La(CodeGen.nowBlock, tmp, rf));
                CodeGen.nowBlock.addInstLast(new LS(CodeGen.nowBlock, reg, tmp, new Imm(0), LS.LSType.flw, AlignmentAnalysis.AlignType.ALIGN_BYTE_8));
            }
            else if (value instanceof Constant.ConstantInt) {
                int init = ((Integer) ((Constant.ConstantInt) value).getConstValue());
                CodeGen.nowBlock.addInstLast(new Li(CodeGen.nowBlock, reg, new Imm(init)));
            }
            else if (value instanceof Constant.ConstantBool) {
                int init = ((Integer) ((Constant.ConstantBool) value).getConstValue());
                CodeGen.nowBlock.addInstLast(new Li(CodeGen.nowBlock, reg, new Imm(init)));
            }
            else if (value instanceof GlobalVariable) {
                RiscvGlobalVar rb = CodeGen.gloMap.get(((GlobalVariable) value).label);
                CodeGen.nowBlock.addInstLast(new La(CodeGen.nowBlock, reg, rb));
            }
            else {
                throw new RuntimeException("wrong const type" + value.getType());
            }
            return reg;
        }
        else {
            if (!map.containsKey(value)) {
                addValue(value);
            }
            return map.get(value);
        }
    }

    // b的绑定为a的寄存器
    public void binding(Value a, Value b) {
        Reg reg = ensureRegForValue(a);
        //绑定在控制流变化后会有bug，如果这个本身的寄存器提前出现呢?
        //所以答案是，提前先看看原本有没有这个寄存器
        if (map.containsKey(b)) {
            CodeGen.nowBlock.addInstLast(new R2(CodeGen.nowBlock, map.get(b), reg, R2.R2Type.mv));
        }
        else {
            map.put(b, reg);
        }
        StackManager.getInstance().bindingValue(nowFunction.getName(), a, b);
    }
}
