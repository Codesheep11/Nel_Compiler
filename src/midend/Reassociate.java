//package midend;
//
//import mir.Function;
//import mir.Module;
//import mir.*;
//import utils.Pair;
//
//import java.util.*;
//
//public class Reassociate {
//    private static ArrayList<BasicBlock> rpot;
//    private static HashMap<BasicBlock, Integer> rankMap = new HashMap<>();
//    private static HashMap<Value, Integer> valueRankMap = new HashMap<>();
//    private static HashMap<Pair<Value, Value>, Integer> pairMap = new HashMap<>();
//    private static ArrayList<Instruction> toRedo = new ArrayList<>();
//
//
//    public static void run(Module module) {
//        for (Function function : module.getFuncSet()) {
//            if (function.isExternal()) continue;
//            runOnFunc(function);
//        }
//    }
//
//    private static void runOnFunc(Function function) {
//        rpot = function.buildReversePostOrderTraversal();
//        boolean madeChange = false;
//        buildRankMap(function);
//        buildPairMap(rpot);
//        for (BasicBlock BB : rpot) {
//            Iterator<Instruction> iter = BB.getInstructions().iterator();
//            while (iter.hasNext()) {
//                Instruction instr = iter.next();
//                if (instr instanceof Instruction.Terminator) continue;
//                if (instr.getUses().size() == 0) {
//                    instr.delete();
//                    madeChange = true;
//                }
//            }
//        }
//        for (Instruction I : new ArrayList<>(toRedo)) {
//            if (isTriviallyDead(I)) {
//                recursivelyEraseDeadInst(I);
//                madeChange = true;
//            }
//            else {
//                optimizeInstruction(I);
//            }
//        }
//        if (madeChange) function.buildControlFlowGraph();
//        clear();
//        return PreservedAnalyses.all();
//    }
//
//
//    private static void clear() {
//        rankMap.clear();
//        valueRankMap.clear();
//        pairMap.clear();
//        toRedo.clear();
//        madeChange = false;
//    }
//
//
//    public static void buildRankMap(Function func) {
//        int rank = 2;
//        for (Function.Argument arg : func.getFuncRArguments()) {
//            valueRankMap.put(arg, rank++);
//        }
//
//        for (BasicBlock BB : rpot) {
//            rankMap.put(BB, (++rank) << 16);
//            for (Instruction instr : BB.getInstructions()) {
//                if (instr.mayHaveNonDefUseDependency()) {
//                    valueRankMap.put(instr, rank++);
//                }
//            }
//        }
//    }
//
//    public void buildPairMap(List<BasicBlock> rpot) {
//        for (BasicBlock BB : rpot) {
//            for (Instruction inst : BB.getInstructions()) {
//                if (!inst.isAssociative() || !(inst instanceof Instruction.BinaryOperation)
//                        || inst.getOperands().size() == 1 || inst.getUses().size() == 0)
//                    continue;
//                HashSet<Value> worklist = new HashSet<>(inst.getOperands());
//                ArrayList<Value> ops = new ArrayList<>();
//                while (!worklist.isEmpty()) {
//                    Value op = worklist.iterator().next();
//                    worklist.remove(op);
//                    if (!(op instanceof Instruction.BinaryOperation) || op.getUses().size() > 1) {
//                        ops.add(op);
//                    }
//                    else if (!inst.isSelfReferencing()) {
//                        Instruction.BinaryOperation binary = (Instruction.BinaryOperation) op;
//                        worklist.add(binary.getOperand_1());
//                        worklist.add(binary.getOperand_2());
//                    }
//                }
//                if (ops.size() <= 10) {
//                    for (int i = 0; i < ops.size(); i++) {
//                        for (int j = i + 1; j < ops.size(); j++) {
//                            pairMap.put(new Pair<>(ops.get(i), ops.get(j)), 1);
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
