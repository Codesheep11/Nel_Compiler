package midend;

import mir.*;
import manager.CentralControl;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * 全局值编号
 *
 * @author Srchycz
 */
public class GlobalValueNumbering {


    private GlobalValueNumbering() {

    }

    public static void run(Module module) {
        if (!CentralControl._GVN_OPEN) return;
        if (!CentralControl._GCM_OPEN) {
            System.out.println("Warning: GVN 依赖于 GCM，请打开 GCM!");
        }
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            run(func);
        }
    }


    public static void run(Function function) {
        function.buildDominanceGraph();
        GVN4Block(function.getEntry(), new HashSet<>(), new HashMap<>());
    }

    /**
     * 对基本块进行全局值编号, 并依照支配树向下传递
     *
     * @param block 基本块
     */
    private static void GVN4Block(BasicBlock block, HashSet<String> records, HashMap<String, Instruction> recordInstructions) {
        Iterator<Instruction> iter = block.getInstructions().iterator();
        ArrayList<Instruction> delList = new ArrayList<>();
        for (Instruction inst : block.getInstructions()){
            // 尝试常量折叠
            if (tryConstantFolding(inst)) {
                delList.add(inst);
                continue;
            }
            if (inst.gvnable()) {
                String key = generateExpressionKey(inst);
                if (records.contains(key)) {
                    inst.replaceAllUsesWith(recordInstructions.get(key));
                    delList.add(inst);
                } else {
                    records.add(key);
                    recordInstructions.put(key, inst);
                }
            }
        }
        delList.forEach(Value::delete);
        for (BasicBlock child : block.getDomTreeChildren()) {
            GVN4Block(child, new HashSet<>(records), new HashMap<>(recordInstructions));
        }
    }

    private static String generateExpressionKey(Instruction inst) {
        return switch (inst.getInstType()) {
            // 满足交换律的操作符
            case FAdd, FMUL, ADD, MUL -> {
                Instruction.BinaryOperation binaryOperation = (Instruction.BinaryOperation) inst;
                String operand1 = binaryOperation.getOperand_1().getDescriptor();
                String operand2 = binaryOperation.getOperand_2().getDescriptor();
                if (operand1.compareTo(operand2) > 0) {
                    // 交换顺序
                    String temp = operand1;
                    operand1 = operand2;
                    operand2 = temp;
                }
                yield inst.getInstType().name() + "," + operand1 + "," + operand2;
            }
            // 不满足交换律的操作符
            case FREM, FSUB, FDIV, REM, SUB, DIV -> {
                Instruction.BinaryOperation binaryOperation = (Instruction.BinaryOperation) inst;
                String operand1 = binaryOperation.getOperand_1().getDescriptor();
                String operand2 = binaryOperation.getOperand_2().getDescriptor();
                yield inst.getInstType().name() + "," + operand1 + "," + operand2;
            }
            case Fcmp, Icmp -> {
                Instruction.Condition compare = (Instruction.Condition) inst;
                String operand1 = compare.getSrc1().getDescriptor();
                String operand2 = compare.getSrc2().getDescriptor();
                yield compare.getCmpOp() + "," + operand1 + "," + operand2;
            }
            case GEP -> {
                Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
                String base = gep.getBase().getDescriptor();
                StringBuilder indices = new StringBuilder();
                for (Value val : gep.getOffsets()) {
                    indices.append(val.getDescriptor());
                    indices.append(",");
                }
                yield inst.getInstType() + "," + base + "," + indices;
            }
            case Zext -> inst.getInstType().name() + "," + inst.getOperands().get(0).toString() + "," + inst.getType();
            case CALL -> {
                Instruction.Call call = (Instruction.Call) inst;
                StringBuilder args = new StringBuilder();
                for (Value val : call.getOperands()) {
                    args.append(val.getDescriptor());
                    args.append(",");
                }
                yield inst.getInstType() + "," + call.getDestFunction().getDescriptor() + "," + args;
            }
            case LOAD -> inst.getInstType().name() + "," + inst.getOperands().get(0).getDescriptor();
            default -> {
                System.out.println("Warning: GVN 未处理类型: " + inst.getInstType() + "!");
                yield inst.toString();
            }
        };
    }

    /**
     * 常量折叠
     */
    private static boolean tryConstantFolding(Instruction instruction) {
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
                    instruction.replaceAllUsesWith(new Constant.ConstantInt(result));
                    return true;
                } else if (instruction.getType().isFloatTy()) {
                    float val1 = (float) op1.getConstValue();
                    float val2 = (float) op2.getConstValue();
                    float result = 0;
                    switch (instruction.getInstType()) {
                        case FAdd -> result = val1 + val2;
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
                    instruction.replaceAllUsesWith(new Constant.ConstantBool(result ? 1 : 0));
                    return true;
                } else if (condition instanceof Instruction.Fcmp) {
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
                    instruction.replaceAllUsesWith(new Constant.ConstantBool(result ? 1 : 0));
                    return true;
                }
            }
        }
        return false;
    }
}

