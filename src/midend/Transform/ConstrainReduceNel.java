//package midend.Transform;
//
//import midend.Analysis.AnalysisManager;
//import midend.Analysis.I32RangeAnalysis;
//import midend.Analysis.result.CFGinfo;
//import midend.Analysis.result.DGinfo;
//import mir.*;
//import mir.Module;
//
//import java.util.BitSet;
//import java.util.HashMap;
//
//public class ConstrainReduceNel {
//
//    private static final int ANALYSIS_DEPTH = 128;
//
//    public static void run(Module module) {
//        for (var func : module.getFuncSet()) {
//            if (func.isExternal()) continue;
//            runOnFunction(func);
//        }
//    }
//
//    private static class IdMap extends HashMap<Value, Integer> {
//        private int cnt = 0;
//
//        @Override
//        public Integer get(Object key) {
//            if (!containsKey(key)) {
//                put((Value) key, cnt++);
//            }
//            return super.get(key);
//        }
//    }
//
//    private static class Constrain {
//
//        public final BitSet[] eqSet = new BitSet[ANALYSIS_DEPTH];
//        public final BitSet[] ltSet = new BitSet[ANALYSIS_DEPTH];
//        public final BitSet[] leqSet = new BitSet[ANALYSIS_DEPTH];
//
//        public Constrain() {
//            for (int i = 0; i < ANALYSIS_DEPTH; i++) {
//                eqSet[i] = new BitSet(ANALYSIS_DEPTH);
//                ltSet[i] = new BitSet(ANALYSIS_DEPTH);
//                leqSet[i] = new BitSet(ANALYSIS_DEPTH);
//            }
//        }
//
//        public Constrain(Constrain constrain) {
//            for (int i = 0; i < ANALYSIS_DEPTH; i++) {
//                eqSet[i] = (BitSet) constrain.eqSet[i].clone();
//                ltSet[i] = (BitSet) constrain.ltSet[i].clone();
//                leqSet[i] = (BitSet) constrain.leqSet[i].clone();
//            }
//        }
//    }
//
//    private static IdMap idMap;
//
//    private static HashMap<BasicBlock, Constrain> constrainMap;
//
//    private static DGinfo dginfo;
//    private static CFGinfo cfginfo;
//
//    private static void runOnFunction(Function func) {
//        cfginfo = AnalysisManager.getCFG(func);
//        dginfo = AnalysisManager.getDG(func);
//
//        idMap = new IdMap();
//        constrainMap = new HashMap<>();
//        runOnBlock(func.getEntry(), new Constrain());
//    }
//
//    private static void runOnBlock(BasicBlock block, Constrain constrain) {
//        constrainMap.put(block, constrain);
//        for (var inst : block.getInstructions()) {
//            switch (inst.getInstType()) {
//                case ADD -> {
//                    Instruction.BinaryOperation binaryOperation = (Instruction.BinaryOperation) inst;
//                    Value op1 = binaryOperation.getOperand_1();
//                    Value op2 = binaryOperation.getOperand_2();
//                    I32RangeAnalysis.I32Range range1 = AnalysisManager.getValueRange(op1, block);
//                    I32RangeAnalysis.I32Range range2 = AnalysisManager.getValueRange(op2, block);
//                    int nowId = idMap.get(inst);
//                    if (!(op2 instanceof Constant)) {
//                        int idx2 = idMap.get(op2);
//                        if (range1.getMinValue() > 0) {
//                            constrain.ltSet[idx2].set(nowId);
//                        } else if (range1.getMinValue() >= 0) {
//                            constrain.leqSet[idx2].set(nowId);
//                        } else if (range1.getMaxValue() < 0) {
//                            constrain.ltSet[nowId].set(idx2);
//                        } else if (range1.getMaxValue() <= 0) {
//                            constrain.leqSet[nowId].set(idx2);
//                        }
//                    }
//                    if (!(op1 instanceof Constant)) {
//                        int idx1 = idMap.get(op1);
//                        if (range2.getMinValue() > 0) {
//                            constrain.ltSet[idx1].set(nowId);
//                        } else if (range2.getMinValue() >= 0) {
//                            constrain.leqSet[idx1].set(nowId);
//                        } else if (range2.getMaxValue() < 0) {
//                            constrain.ltSet[nowId].set(idx1);
//                        } else if (range2.getMaxValue() <= 0) {
//                            constrain.leqSet[nowId].set(idx1);
//                        }
//                    }
//
//                }
//                case MUL -> {
//
//                }
//                default -> {
//                }
//            }
//        }
//        for (BasicBlock child : dginfo.getDomTreeChildren(block)) {
//            Constrain childConstrain = new Constrain();
//            if (block.getTerminator() instanceof Instruction.Branch branch) {
//                Value cond = branch.getCond();
//                if (cond instanceof Instruction.Icmp icmp) {
//                    if (!(icmp.getSrc1() instanceof Constant) && !(icmp.getSrc2() instanceof Constant)) {
//                        int idx1 = idMap.get(icmp.getSrc1());
//                        int idx2 = idMap.get(icmp.getSrc2());
//                        Instruction.Icmp.CondCode condCode = icmp.getCondCode();
//                    }
//                }
//            }
//            runOnBlock(child, childConstrain);
//        }
//    }
//}
//0