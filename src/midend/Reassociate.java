package midend;

import mir.Function;
import mir.Module;
import mir.*;

import java.util.*;

public class Reassociate {
    private static ArrayList<BasicBlock> rpot;
    private static HashMap<BasicBlock, Integer> rankMap = new HashMap<>();
    private static HashMap<Value, Integer> valueRankMap = new HashMap<>();
    private static HashMap<Pair<Value, Value>, Integer> pairMap = new HashMap<>();
    private static ArrayList<Instruction> toRedo = new ArrayList<>();
    private static boolean madeChange = false;


    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    private static void runOnFunc(Function function) {
        rpot = function.buildReversePostOrderTraversal();
        buildRankMap(function);
        buildPairMap(rpot);
        for (BasicBlock BB : rpot) {
            List<Instruction> instructions = new ArrayList<>(BB.getInstructions());
            for (Instruction I : instructions) {
                if (isTriviallyDead(I)) {
                    deleteInstruction(I);
                }
                else {
                    optimizeInstruction(I);
                }
            }
        }
        for (Instruction I : new ArrayList<>(toRedo)) {
            if (isTriviallyDead(I)) {
                recursivelyEraseDeadInst(I);
                madeChange = true;
            }
            else {
                optimizeInstruction(I);
            }
        }
        if (madeChange) function.buildControlFlowGraph();
        clear();
        return PreservedAnalyses.all();
    }


    private static void clear() {
        rankMap.clear();
        valueRankMap.clear();
        pairMap.clear();
        toRedo.clear();
        madeChange = false;
    }


    public static void buildRankMap(Function func) {
        int rank = 2;
        for (Function.Argument arg : func.getFuncRArguments()) {
            valueRankMap.put(arg, rank++);
        }

        for (BasicBlock BB : rpot) {
            rankMap.put(BB, (++rank) << 16);
            for (Instruction instr : BB.getInstructions()) {
                if (instr.mayHaveNonDefUseDependency()) {
                    valueRankMap.put(instr, rank++);
                }
            }
        }
    }

    public void buildPairMap(List<BasicBlock> rpot) {
        for (BasicBlock BB : rpot) {
            for (Instruction I : BB.getInstructions()) {
                // Skip instructions that do not meet the criteria
                if (!isAssociative(I) || !isBinaryOp(I) || hasSingleUse(I) || isLastUse(I)) {
                    continue;
                }

                Set<Value> worklist = new HashSet<>(Arrays.asList(I.getOperand(0), I.getOperand(1)));
                List<Value> ops = new ArrayList<>();

                // Process worklist
                while (!worklist.isEmpty()) {
                    Value op = worklist.iterator().next();
                    worklist.remove(op);

                    // If operand is not a binary op or has multiple uses, add to ops
                    if (!isBinaryOp(op) || hasMultipleUses(op)) {
                        ops.add(op);
                    }
                    else if (!isSelfReferencing(op)) {
                        worklist.add(op.getOperand(0));
                        worklist.add(op.getOperand(1));
                    }
                }

                // Record operand pairs in pairMap
                if (ops.size() <= 10) { // Arbitrary limit for example
                    for (int i = 0; i < ops.size(); i++) {
                        for (int j = i + 1; j < ops.size(); j++) {
                            pairMap.put(new Pair<>(ops.get(i), ops.get(j)), 1);
                        }
                    }
                }
            }
        }
    }
}
