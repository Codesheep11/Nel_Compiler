package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Analysis.I32RangeAnalysis;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

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
            }
            if (instr instanceof Instruction.Icmp icmp) {
                Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                I32RangeAnalysis.I32Range r1 = AnalysisManager.getValueRange(icmp.getSrc1(), basicBlock);
                I32RangeAnalysis.I32Range r2 = AnalysisManager.getValueRange(icmp.getSrc2(), basicBlock);
                if (condCode == Instruction.Icmp.CondCode.EQ) {
                    if (r1.getMinValue() > r2.getMaxValue() || r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.NE) {
                    if (r1.getMinValue() > r2.getMaxValue() || r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(1));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SGE) {
                    if (r1.getMinValue() >= r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(1));
                    }
                    else if (r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SGT) {
                    if (r1.getMinValue() > r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(1));
                    }
                    else if (r1.getMaxValue() <= r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SLE) {
                    if (r1.getMaxValue() <= r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(1));
                    }
                    else if (r1.getMinValue() > r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(0));
                    }
                }
                else if (condCode == Instruction.Icmp.CondCode.SLT) {
                    if (r1.getMaxValue() < r2.getMinValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(1));
                    }
                    else if (r1.getMinValue() >= r2.getMaxValue()) {
                        delList.add(instr);
                        instr.replaceAllUsesWith(Constant.ConstantBool.get(0));
                    }
                }
            }
            else if (instr instanceof Instruction.Rem rem) {
                I32RangeAnalysis.I32Range r1 = AnalysisManager.getValueRange(rem.getOperand_1(), basicBlock);
                I32RangeAnalysis.I32Range r2 = AnalysisManager.getValueRange(rem.getOperand_2(), basicBlock);
                if (r1.getMinValue() > r2.getMinValue() && r1.getMaxValue() < r2.getMaxValue()) {
                    delList.add(instr);
                    instr.replaceAllUsesWith(rem.getOperand_1());
                }
            }
        }
        delList.forEach(Value::delete);
    }
}
