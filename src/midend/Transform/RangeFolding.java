package midend.Transform;

import backend.operand.Operand;
import midend.Analysis.I32RangeAnalysis;
import mir.Module;
import mir.*;

import java.util.ArrayList;

public class RangeFolding {
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        for (BasicBlock basicBlock : function.getBlocks()) {
            runOnBlock(basicBlock);
        }
    }

    public static void runOnBlock(BasicBlock basicBlock) {
        ArrayList<Instruction> delList = new ArrayList<>();
        for (Instruction instr : basicBlock.getInstructionsSnap()) {
            if (instr.getType().isInt32Ty()) {
                I32RangeAnalysis.I32Range range = I32RangeAnalysis.getValueRange(instr);
                if (range.getMaxValue() == range.getMinValue()) {
                    Constant constant = new Constant.ConstantInt(range.getMaxValue());
                    instr.replaceAllUsesWith(constant);
                    delList.add(instr);
                }
            }
            if (instr instanceof Instruction.Icmp icmp) {
                Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                I32RangeAnalysis.I32Range r1 = I32RangeAnalysis.getValueRange(icmp.getSrc1());
                I32RangeAnalysis.I32Range r2 = I32RangeAnalysis.getValueRange(icmp.getSrc2());
                if (condCode == Instruction.Icmp.CondCode.SGE) {
                    if (r1.getMinValue() >= r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(1));
                    }
                    else if (r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SGT) {
                    if (r1.getMinValue() > r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(1));
                    }
                    else if (r1.getMaxValue() <= r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SLE) {
                    if (r1.getMaxValue() <= r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(1));
                    }
                    else if (r1.getMinValue() > r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SLT) {
                    if (r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(1));
                    }
                    else if (r1.getMinValue() >= r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(new Constant.ConstantBool(0));
                    }
                }
            }
        }
        delList.forEach(Value::delete);
    }
}
