package midend;

import mir.*;
import manager.CentralControl;
import mir.Module;

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
        while (iter.hasNext()) {
            Instruction inst = iter.next();
            // 尝试常量折叠
            if (tryConstantFolding(inst)) {
                iter.remove();
                continue;
            }
            if (inst.gvnable()) {
                String key = generateExpressionKey(inst);
                if (records.contains(key)) {
                    inst.replaceAllUsesWith(recordInstructions.get(key));
                    iter.remove();
                } else {
                    records.add(key);
                    recordInstructions.put(key, inst);
                }
            }
        }
        for (BasicBlock child : block.getDomTreeChildren()) {
            GVN4Block(child, new HashSet<>(records), new HashMap<>(recordInstructions));
        }
    }

    private static String generateExpressionKey(Instruction inst) {
        switch (inst.getInstType()) {
            // 满足交换律的操作符
            case FAdd:
            case FMUL:
            case ADD:
            case MUL: {
                Instruction.BinaryOperation binaryOperation = (Instruction.BinaryOperation) inst;
                String operand1 = binaryOperation.getOperand_1().getDescriptor();
                String operand2 = binaryOperation.getOperand_2().getDescriptor();
                if (operand1.compareTo(operand2) > 0) {
                    // 交换顺序
                    String temp = operand1;
                    operand1 = operand2;
                    operand2 = temp;
                }
                return inst.getInstType().name() + "," + operand1 + "," + operand2;
            }
            // 不满足交换律的操作符
            case FREM:
            case FSUB:
            case FDIV:
            case REM:
            case SUB:
            case DIV: {
                Instruction.BinaryOperation binaryOperation = (Instruction.BinaryOperation) inst;
                String operand1 = binaryOperation.getOperand_1().getDescriptor();
                String operand2 = binaryOperation.getOperand_2().getDescriptor();
                return inst.getInstType().name() + "," + operand1 + "," + operand2;
            }
            case Fcmp:
            case Icmp: {
                Instruction.Condition compare = (Instruction.Condition) inst;
                String operand1 = compare.getSrc1().getDescriptor();
                String operand2 = compare.getSrc2().getDescriptor();
                return compare.getCmpOp() + "," + operand1 + "," + operand2;
            }
            case GEP: {
                Instruction.GetElementPtr gep = (Instruction.GetElementPtr) inst;
                String base = gep.getBase().getDescriptor();
                StringBuilder indices = new StringBuilder();
                for (Value val : gep.getOffsets()) {
                    indices.append(val.getDescriptor());
                    indices.append(",");
                }
                return inst.getInstType() + "," + base + "," + indices;
            }
            case Zext: {
                return inst.getInstType().name() + "," + inst.getOperands().get(0).toString() + "," + inst.getType();
            }
            default: {
                System.out.println("Warning: GVN 未处理类型: " + inst.getInstType() + "!");
                return inst.toString();
            }
        }
    }

    /**
     * 常量折叠 <br>
     * NOTE: 现在只折叠了运算结果为 int32 和 float32的指令，之后可以考虑 int1 是否可以折叠
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
        return false;
    }
}

