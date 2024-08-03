package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Analysis.I32RangeAnalysis;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

public class RangeFolding {
    public static boolean run(Module module) {
        boolean modified = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            modified |= runOnFunc(function);
        }
        return modified;
    }

    public static boolean runOnFunc(Function function) {
        AnalysisManager.refreshI32Range(function);
        boolean modified = false;
        for (BasicBlock basicBlock : function.getBlocks()) {
            modified |= runOnBlock(basicBlock);
        }
        return modified;
    }

    public static boolean runOnBlock(BasicBlock basicBlock) {
        ArrayList<Instruction> delList = new ArrayList<>();
        for (Instruction instr : basicBlock.getInstructionsSnap()) {
            HashMap<Value, Constant> operand2Const = new HashMap<>();
            for (Value operand : instr.getOperands()) {
                if (!operand.getType().isInt32Ty()) continue;
                if (operand instanceof Instruction || operand instanceof Function.Argument) {
                    I32RangeAnalysis.I32Range range = AnalysisManager.getValueRange(operand, basicBlock);
                    if (range.getMaxValue() == range.getMinValue()) {
                        Constant constant = Constant.ConstantInt.get(range.getMaxValue());
                        operand2Const.put(operand, constant);
                    }
                }
            }
            for (Value operand : operand2Const.keySet()) {
                instr.replaceUseOfWith(operand, operand2Const.get(operand));
            }
        }
        for (Instruction instr : basicBlock.getInstructionsSnap()) {
            if (instr.getType().isInt32Ty()) {
                I32RangeAnalysis.I32Range range = AnalysisManager.getValueRange(instr, basicBlock);
                if (range.getMaxValue() == range.getMinValue()) {
                    Constant constant = Constant.ConstantInt.get(range.getMaxValue());
                    instr.replaceAllUsesWith(constant);
                    delList.add(instr);
                }
            }else {
                continue;
            }
            if (instr instanceof Instruction.Icmp icmp) {
                Value v = icmpSimplify(icmp);
                if (v instanceof Constant) {
                    delList.add(icmp);
                    instr.replaceAllUsesWith(v);
                }
            }
            else if (instr instanceof Instruction.BinaryOperation binaryOperation) {
                I32RangeAnalysis.I32Range r1 = AnalysisManager.getValueRange(binaryOperation.getOperand_1(), basicBlock);
                I32RangeAnalysis.I32Range r2 = AnalysisManager.getValueRange(binaryOperation.getOperand_2(), basicBlock);
                if (binaryOperation instanceof Instruction.Rem rem) {
                    if (r1.getMinValue() > 0 && r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(rem.getOperand_1());
                    }
                }
                else if (binaryOperation instanceof Instruction.Min min) {
                    if (r1.getMinValue() >= r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(min.getOperand_2());
                    }
                    else if (r1.getMaxValue() <= r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(min.getOperand_1());
                    }
                }
                else if (binaryOperation instanceof Instruction.Max max) {
                    if (r1.getMinValue() >= r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(max.getOperand_1());
                    }
                    else if (r1.getMaxValue() <= r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(max.getOperand_2());
                    }
                }
            }
        }
        delList.forEach(Value::delete);
        return !delList.isEmpty();
    }

    public static Value icmpSimplify(Instruction.Icmp icmp) {
        BasicBlock basicBlock = icmp.getParentBlock();
        Instruction.Icmp.CondCode condCode = icmp.getCondCode();
        I32RangeAnalysis.I32Range r1 = AnalysisManager.getValueRange(icmp.getSrc1(), basicBlock);
        I32RangeAnalysis.I32Range r2 = AnalysisManager.getValueRange(icmp.getSrc2(), basicBlock);
        switch (condCode) {
            case EQ -> {
                if (r1.getMinValue() > r2.getMaxValue() || r1.getMaxValue() < r2.getMinValue()) {
                    return Constant.ConstantBool.get(0);
                }
            }
            case NE -> {
                if (r1.getMinValue() > r2.getMaxValue() || r1.getMaxValue() < r2.getMinValue()) {
                    return Constant.ConstantBool.get(1);
                }
            }
            case SGE -> {
                if (r1.getMinValue() >= r2.getMaxValue()) {
                    return Constant.ConstantBool.get(1);
                }
                else if (r1.getMaxValue() < r2.getMinValue()) {
                    return Constant.ConstantBool.get(0);
                }
            }
            case SGT -> {
                if (r1.getMinValue() > r2.getMaxValue()) {
                    return Constant.ConstantBool.get(1);
                }
                else if (r1.getMaxValue() <= r2.getMinValue()) {
                    return Constant.ConstantBool.get(0);
                }
            }
            case SLE -> {
                if (r1.getMaxValue() <= r2.getMinValue()) {
                    return Constant.ConstantBool.get(1);
                }
                else if (r1.getMinValue() > r2.getMaxValue()) {
                    return Constant.ConstantBool.get(0);
                }
            }
            case SLT -> {
                if (r1.getMaxValue() < r2.getMinValue()) {
                    return Constant.ConstantBool.get(1);
                }
                else if (r1.getMinValue() >= r2.getMaxValue()) {
                    return Constant.ConstantBool.get(0);
                }
            }
        }
        return icmp;
    }
}
