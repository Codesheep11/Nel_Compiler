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
        for (Function func : module.getFuncSet()) {
            if(func.isExternal()) continue;
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
                }
                else {
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
                String operand1 = inst.getOperands().get(0).getName();
                String operand2 = inst.getOperands().get(1).getName();
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
            case Fcmp:
            case Icmp:
            case REM:
            case SUB:
            case DIV: {
                String operand1 = inst.getOperands().get(0).getName();
                String operand2 = inst.getOperands().get(1).getName();
                return inst.getInstType().name() + "," + operand1 + "," + operand2;
            }
            case GEP: {
                String base = inst.getOperands().get(0).getName();
                StringBuilder indices = new StringBuilder();
                for (int i = 1; i < inst.getOperands().size(); i++) {
                    indices.append(inst.getOperands().get(i).getName());
                    if (i != inst.getOperands().size() - 1) {
                        indices.append(",");
                    }
                }
                return inst.getInstType() + "," + base + "," + indices;
            }
            case Zext: {
                return inst.getInstType().name() + "," + inst.getOperands().get(0).toString() + "," + inst.getType();
            }
            default: {
                System.out.println("Warning: GVN 过程遇到不明确类型！");
                return inst.toString();
            }
        }
    }

    /**
     * 常量折叠 <br>
     * NOTE: 现在只折叠了运算结果为 int32 和 float32的指令，之后可以考虑 int1 是否可以折叠
     */
    private static boolean tryConstantFolding(Instruction instruction) {
        if (instruction.getOperands().size() == 2) {
            Value operand1 = instruction.getOperands().get(0);
            Value operand2 = instruction.getOperands().get(1);
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
                }
                else if (instruction.getType().isFloatTy()) {
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

