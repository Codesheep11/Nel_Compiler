package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Analysis.MemDepAnalysis;
import midend.Analysis.PointerBaseAnalysis;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 值编号
 *
 */
public class LocalValueNumbering {


    private LocalValueNumbering() {

    }

    public static boolean run(Module module) {
        boolean modified = false;
        for (Function func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            modified |= runOnFunc(func);
        }
        return modified;
    }


    public static boolean runOnFunc(Function function) {
        AnalysisManager.refreshDG(function);
        PointerBaseAnalysis.runOnFunc(function);
        return GVN4Block(function.getEntry(), new HashSet<>(), new HashMap<>());
    }

    /**
     * 对基本块进行全局值编号, 并依照支配树向下传递
     *
     * @param block 基本块
     */
    private static boolean GVN4Block(BasicBlock block, HashSet<String> records, HashMap<String, Instruction> recordInstructions) {
        boolean modified = false;
        ArrayList<Instruction> delList = new ArrayList<>();
        for (Instruction inst : block.getInstructions()) {
            // 尝试常量折叠
            if (ConstantFolding.tryConstantFolding(inst)) {
                delList.add(inst);
                continue;
            }
            if (inst.lvnable()) {
                String key = generateExpressionKey(inst);
                if (records.contains(key)) {
                    if (inst instanceof Instruction.Load load) {
                        // 如果是load指令，需要检查是否有修改
                        Instruction record = recordInstructions.get(key);
                        if (MemDepAnalysis.assureNotWritten(block.getParentFunction(), record.getParentBlock(), block, load.getAddr())) {
                            inst.replaceAllUsesWith(record);
                            delList.add(inst);
                            modified = true;
                        }
                        else {
                            recordInstructions.put(key, inst);
                        }
                    }
                    else {
                        inst.replaceAllUsesWith(recordInstructions.get(key));
                        delList.add(inst);
                        modified = true;
                    }
                }
                else {
                    records.add(key);
                    recordInstructions.put(key, inst);
                }
            }
        }
        delList.forEach(Value::delete);
        for (BasicBlock child : block.getDomTreeChildren()) {
            modified |= GVN4Block(child, new HashSet<>(records), new HashMap<>(recordInstructions));
        }
        return modified;
    }

    private static String generateExpressionKey(Instruction inst) {
        return switch (inst.getInstType()) {
            // 满足交换律的操作符
            case FADD, FMUL, ADD, MUL, MAX, MIN, FMAX, FMIN -> {
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
            case FMADD, FMSUB, FNMADD, FNMSUB -> {
                Instruction.TernaryOperation tripleOp = (Instruction.TernaryOperation) inst;
                String operand1 = tripleOp.getOperand_1().getDescriptor();
                String operand2 = tripleOp.getOperand_2().getDescriptor();
                String operand3 = tripleOp.getOperand_3().getDescriptor();
                if (operand1.compareTo(operand2) > 0) {
                    // 交换顺序
                    String temp = operand1;
                    operand1 = operand2;
                    operand2 = temp;
                }
                yield inst.getInstType().name() + "," + operand1 + "," + operand2 + "," + operand3;
            }
            case FNEG, LOAD, FABS -> inst.getInstType().name() + "," + inst.getOperands().get(0).getDescriptor();
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
            default -> {
                System.out.println("Warning: LVN 未处理类型: " + inst.getInstType() + "!");
                yield inst.toString();
            }
        };
    }

}

