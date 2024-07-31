package midend.Transform;

import midend.Analysis.AnalysisManager;
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
            runOnFunc(func);
        }
    }


    public static void runOnFunc(Function function) {
        AnalysisManager.refreshDG(function);
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
            if (ConstantFolding.tryConstantFolding(inst)) {
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
            case FAdd, FMUL, ADD, MUL, MAX, MIN -> {
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

}

