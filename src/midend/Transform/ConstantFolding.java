package midend.Transform;

import manager.CentralControl;
import mir.*;
import mir.Module;

import java.util.ArrayList;

public class ConstantFolding {

    public static boolean run(Module module) {
        boolean modified = false;
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            modified |= runOnFunc(func);
        }
        return modified;
    }

    public static boolean runOnFunc(Function function) {
        boolean modified = false;
        ArrayList<Instruction> delList = new ArrayList<>();
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (tryConstantFolding(inst)) {
                    delList.add(inst);
                    modified = true;
                }
            }
        }
        delList.forEach(Instruction::delete);
        return modified;
    }

    /**
     * 常量折叠
     */
    public static boolean tryConstantFolding(Instruction instruction) {
        if (instruction instanceof Instruction.BinaryOperation binaryOperation) {
            Value operand1 = binaryOperation.getOperand_1();
            Value operand2 = binaryOperation.getOperand_2();
            if (operand1 instanceof Constant op1 && operand2 instanceof Constant op2) {
                if (instruction.getType().isInt32Ty()) {
                    int val1 = (int) op1.getConstValue();
                    int val2 = (int) op2.getConstValue();
                    int result = 0;
                    switch (instruction.getInstType()) {
                        case ADD -> result = val1 + val2;
                        case SUB -> result = val1 - val2;
                        case MUL -> result = val1 * val2;
                        case DIV -> result = val1 / val2;
                        case REM -> result = val1 % val2;
                        default -> {
                        }
                    }
                    instruction.replaceAllUsesWith(Constant.ConstantInt.get(result));
                    return true;
                }
                else if (instruction.getType().isFloatTy()) {
                    float val1 = (float) op1.getConstValue();
                    float val2 = (float) op2.getConstValue();
                    float result = 0;
                    switch (instruction.getInstType()) {
                        case FADD -> result = val1 + val2;
                        case FSUB -> result = val1 - val2;
                        case FMUL -> result = val1 * val2;
                        case FDIV -> result = val1 / val2;
                        case FREM -> result = val1 % val2;
                        default -> {
                        }
                    }
                    instruction.replaceAllUsesWith(new Constant.ConstantFloat(result));
                    return true;
                }
            }
        }
        else if (instruction instanceof Instruction.Condition condition) {
            Value operand1 = condition.getSrc1();
            Value operand2 = condition.getSrc2();
            if (operand1 instanceof Constant op1 && operand2 instanceof Constant op2) {
                if (condition instanceof Instruction.Icmp) {
                    int val1 = (int) op1.getConstValue();
                    int val2 = (int) op2.getConstValue();
                    boolean result = false;
                    switch (condition.getCmpOp()) {
                        case "eq" -> result = (val1 == val2);
                        case "ne" -> result = (val1 != val2);
                        case "slt" -> result = (val1 < val2);
                        case "sle" -> result = (val1 <= val2);
                        case "sgt" -> result = (val1 > val2);
                        case "sge" -> result = (val1 >= val2);
                        default -> {
                        }
                    }
                    instruction.replaceAllUsesWith(Constant.ConstantBool.get(result ? 1 : 0));
                    return true;
                }
                else if (condition instanceof Instruction.Fcmp) {
                    float val1 = (float) op1.getConstValue();
                    float val2 = (float) op2.getConstValue();
                    boolean result = false;
                    switch (condition.getCmpOp()) {
                        case "oeq" -> result = (Math.abs(val1 - val2) < CentralControl.epsilon);
                        case "one" -> result = (Math.abs(val1 - val2) >= CentralControl.epsilon);
                        case "olt" -> result = (val1 < val2);
                        case "ole" -> result = (val1 < val2 || Math.abs(val1 - val2) < CentralControl.epsilon);
                        case "ogt" -> result = (val1 > val2);
                        case "oge" -> result = (val1 > val2 || Math.abs(val1 - val2) < CentralControl.epsilon);
                        default -> {
                        }
                    }
                    instruction.replaceAllUsesWith(Constant.ConstantBool.get(result ? 1 : 0));
                    return true;
                }
            }
        }
        else if(instruction instanceof Instruction.Sext sext) {
            if (sext.getSrc() instanceof Constant.ConstantInt) {
                int val = (int) ((Constant.ConstantInt) sext.getSrc()).getConstValue();
                instruction.replaceAllUsesWith(Constant.ConstantInt.get(val));
                return true;
            }
        }
        return false;
    }
}
