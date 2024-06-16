package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instruction;

import java.util.HashMap;

/**
 * 全局值编号
 * @author Srchycz
 */
public class GlobalValueNumbering {
    HashMap<BasicBlock, HashMap<String, Integer>> valueNumberTables;
    HashMap<BasicBlock, HashMap<String, Instruction>> valueNumberInstructions;


    public GlobalValueNumbering() {
        valueNumberTables = new HashMap<>();
    }
    public static void run(Function function) {
        GlobalValueNumbering gvn = new GlobalValueNumbering();
        for (BasicBlock basicBlock : function.getBlocks()) {
            gvn.valueNumberTables.put(basicBlock, new HashMap<>());
            for (Instruction inst : basicBlock.getInstructions()) {
                String key = gvn.generateExpressionKey(inst);
            }
        }
        mergeValueNumberTables(function);
    }

    private int getOrAssignValueNumber(Instruction inst) {
        if (!valueNumberTables.containsKey(inst.toString())) {
            valueNumberTables.put(inst.toString(), nextValueNumber++);
        }
        return valueNumberTables.get(inst.toString());
    }

    private String generateExpressionKey(Instruction inst) {
        switch (inst.getInstType()) {
            // 满足交换律的操作符
            case FAdd:
            case FMUL:
            case ADD:
            case MUL:{
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
            case DIV:{
                String operand1 = inst.getOperands().get(0).getName();
                String operand2 = inst.getOperands().get(1).getName();
                return inst.getInstType().name() + "," + operand1 + "," + operand2;
            }
            case GEP:{
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
            case Zext:{
                return inst.getInstType().name() + "," + inst.getOperands().get(0).toString() + "," + inst.getType();
            }
            default: return inst.toString();
        }
    }

    private boolean canGVN(Instruction inst) {
        return switch (inst.getInstType()) {
            case ALLOC, LOAD, STORE, CALL, PHI, RETURN, BitCast, SItofp, FPtosi, BRANCH, PHICOPY, MOVE -> false;
            default -> true;
        };
    }

    private void mergeValueNumberTables(Function function) {
        // Handle merging of value number tables at control flow join points
    }

    private void constantFolding(Instruction instruction) {
        // Handle constant folding

    }
}

