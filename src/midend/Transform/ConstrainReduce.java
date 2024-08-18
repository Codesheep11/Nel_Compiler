package midend.Transform;

import midend.Analysis.AnalysisManager;
import midend.Analysis.I32RangeAnalysis;
import midend.Analysis.result.CFGinfo;
import midend.Analysis.result.DGinfo;
import mir.*;
import mir.Module;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;

public class ConstrainReduce {

    private static final int ANALYSIS_DEPTH = 128;

    public static void run(Module module) {
        for (var func : module.getFuncSet()) {
            if (func.isExternal()) continue;
            runOnFunction(func);
        }
    }

    private static class IdMap extends HashMap<Value, Integer> {
        private int cnt = 0;

        @Override
        public Integer get(Object key) {
            if (!containsKey(key)) {
                put((Value) key, cnt++);
            }
            return super.get(key);
        }
    }

    private enum Result {
        UNKNOWN, TRUE, FALSE
    }

    private static class Constrain {

        public final BitSet[] eqSet = new BitSet[ANALYSIS_DEPTH];
        public final BitSet[] ltSet = new BitSet[ANALYSIS_DEPTH];
        public final BitSet[] leqSet = new BitSet[ANALYSIS_DEPTH];

        public Constrain() {
            Arrays.setAll(eqSet, i -> new BitSet(ANALYSIS_DEPTH));
            Arrays.setAll(ltSet, i -> new BitSet(ANALYSIS_DEPTH));
            Arrays.setAll(leqSet, i -> new BitSet(ANALYSIS_DEPTH));
        }

        public Constrain(Constrain constrain) {
            for (int i = 0; i < ANALYSIS_DEPTH; i++) {
                eqSet[i] = (BitSet) constrain.eqSet[i].clone();
                ltSet[i] = (BitSet) constrain.ltSet[i].clone();
                leqSet[i] = (BitSet) constrain.leqSet[i].clone();
            }
        }

        public void setRelation(int a, int b, Instruction.Icmp.CondCode condCode) {
            switch (condCode) {
                case EQ -> {
                    eqSet[a].set(b);
                    eqSet[b].set(a);
                }
                case SLT -> ltSet[a].set(b);
                case SLE -> leqSet[a].set(b);
                case SGT -> ltSet[b].set(a);
                case SGE -> leqSet[b].set(a);
                default -> {
                }
            }
        }

        public Result checkRelation(int a, int b, Instruction.Icmp.CondCode condCode) {
            return switch (condCode) {
                case EQ -> {
                    if (eqSet[a].get(b)) yield Result.TRUE;
                    if (ltSet[a].get(b) || ltSet[b].get(a)) yield Result.FALSE;
                    yield Result.UNKNOWN;
                }
                case SLT -> {
                    if (ltSet[a].get(b)) yield Result.TRUE;
                    if (eqSet[a].get(b) || leqSet[b].get(a)) yield Result.FALSE;
                    yield Result.UNKNOWN;
                }
                case SLE -> {
                    if (leqSet[a].get(b) || eqSet[a].get(b) || ltSet[a].get(b)) yield Result.TRUE;
                    if (ltSet[b].get(a)) yield Result.FALSE;
                    yield Result.UNKNOWN;
                }
                case SGT -> {
                    if (ltSet[b].get(a)) yield Result.TRUE;
                    if (eqSet[b].get(a) || leqSet[a].get(b)) yield Result.FALSE;
                    yield Result.UNKNOWN;
                }
                case SGE -> {
                    if (leqSet[b].get(a) || eqSet[b].get(a) || ltSet[b].get(a)) yield Result.TRUE;
                    if (ltSet[a].get(b)) yield Result.FALSE;
                    yield Result.UNKNOWN;
                }
                case NE -> {
                    if (eqSet[a].get(b)) yield Result.FALSE;
                    if (ltSet[a].get(b) || ltSet[b].get(a)) yield Result.TRUE;
                    yield Result.UNKNOWN;
                }
                default -> Result.UNKNOWN;
            };
        }

        public void localInfer() {
            for (int i = 0; i < idMap.cnt; ++i) {
                for (int j = i + 1; j < idMap.cnt; ++j) {
                    if (eqSet[i].get(j)) {
                        ltSet[i].or(ltSet[j]);
                        ltSet[j].or(ltSet[i]);
                        leqSet[i].or(leqSet[j]);
                        leqSet[j].or(leqSet[i]);
                    }
                }
            }
        }

        public void floydSpread() {
            floydSpread(ANALYSIS_DEPTH);
        }

        public void floydSpread(int n) {
            if (n > ANALYSIS_DEPTH) {
                throw new RuntimeException("Analysis depth too large");
            }
            for (int k = 0; k < n; k++) {
                for (int i = 0; i < n; i++) {
                    if (eqSet[i].get(k)) {
                        eqSet[i].or(eqSet[k]);
                    }
                    if (ltSet[i].get(k)) {
                        ltSet[i].or(ltSet[k]);
                    }
                    if (leqSet[i].get(k)) {
                        leqSet[i].or(leqSet[k]);
                    }
                }
            }
        }
    }

    private static IdMap idMap;

    private static HashMap<BasicBlock, Constrain> constrainMap;

    private static DGinfo dginfo;
    private static CFGinfo cfginfo;

    private static void runOnFunction(Function func) {
        cfginfo = AnalysisManager.getCFG(func);
        dginfo = AnalysisManager.getDG(func);
        AnalysisManager.refreshI32Range(func);
        idMap = new IdMap();
        constrainMap = new HashMap<>();
        runOnBlock(func.getEntry(), new Constrain());
    }

    @SuppressWarnings({"SwitchStatementWithTooFewBranches"})
    private static void runOnBlock(BasicBlock block, Constrain constrain) {
        constrainMap.put(block, constrain);
        if (idMap.size() >= ANALYSIS_DEPTH) return;
        for (var inst : block.getInstructions()) {
            switch (inst.getInstType()) {
                case ADD -> {
                    Instruction.BinaryOperation binaryOperation = (Instruction.BinaryOperation) inst;
                    Value op1 = binaryOperation.getOperand_1();
                    Value op2 = binaryOperation.getOperand_2();
                    I32RangeAnalysis.I32Range range1 = AnalysisManager.getValueRange(op1, block);
                    I32RangeAnalysis.I32Range range2 = AnalysisManager.getValueRange(op2, block);
                    int nowId = idMap.get(inst);
                    if (idMap.size() >= ANALYSIS_DEPTH) return;
                    if (!(op2 instanceof Constant)) {
                        int idx2 = idMap.get(op2);
                        if (idMap.size() >= ANALYSIS_DEPTH) return;
                        if (range1.getMinValue() > 0) {
                            constrain.ltSet[idx2].set(nowId);
                        } else if (range1.getMinValue() >= 0) {
                            constrain.leqSet[idx2].set(nowId);
                        } else if (range1.getMaxValue() < 0) {
                            constrain.ltSet[nowId].set(idx2);
                        } else if (range1.getMaxValue() <= 0) {
                            constrain.leqSet[nowId].set(idx2);
                        }
                    }
                    if (!(op1 instanceof Constant)) {
                        int idx1 = idMap.get(op1);
                        if (idMap.size() >= ANALYSIS_DEPTH) return;
                        if (range2.getMinValue() > 0) {
                            constrain.ltSet[idx1].set(nowId);
                        } else if (range2.getMinValue() >= 0) {
                            constrain.leqSet[idx1].set(nowId);
                        } else if (range2.getMaxValue() < 0) {
                            constrain.ltSet[nowId].set(idx1);
                        } else if (range2.getMaxValue() <= 0) {
                            constrain.leqSet[nowId].set(idx1);
                        }
                    }
                }
                default -> {
                }
            }
        }

        reduceConstrain(block);

        for (BasicBlock child : dginfo.getDomTreeChildren(block)) {
            Constrain childConstrain = new Constrain(constrain);
            if (block.getTerminator() instanceof Instruction.Branch branch) {
                Value cond = branch.getCond();
                if (cond instanceof Instruction.Icmp icmp) {
                    if (!(icmp.getSrc1() instanceof Constant) && !(icmp.getSrc2() instanceof Constant)) {
                        int idx1 = idMap.get(icmp.getSrc1());
                        int idx2 = idMap.get(icmp.getSrc2());
                        if (idMap.size() >= ANALYSIS_DEPTH) return;
                        Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                        if (cfginfo.getPredBlocks(child).size() == 1) {
                            if (branch.getThenBlock() == child) {
                                childConstrain.setRelation(idx1, idx2, condCode);
                            }
                            if (branch.getElseBlock() == child) {
                                childConstrain.setRelation(idx1, idx2, condCode.inverse());
                            }
                        }
                    }
                }
            }
            runOnBlock(child, childConstrain);
        }
    }

    private static void reduceConstrain(BasicBlock block) {
        Constrain constrain = constrainMap.get(block);
        constrain.floydSpread();
        constrain.localInfer();

        if (block.getTerminator() instanceof Instruction.Branch branch) {
            Value cond = branch.getCond();
            if (cond instanceof Instruction.Icmp icmp) {
                if (!(icmp.getSrc1() instanceof Constant) && !(icmp.getSrc2() instanceof Constant)) {
                    int idx1 = idMap.get(icmp.getSrc1());
                    int idx2 = idMap.get(icmp.getSrc2());
                    Instruction.Icmp.CondCode condCode = icmp.getCondCode();
                    Result result = constrain.checkRelation(idx1, idx2, condCode);
                    if (result == Result.TRUE) {
                        branch.setCond(Constant.ConstantBool.get(true));
                    } else if (result == Result.FALSE) {
                        branch.setCond(Constant.ConstantBool.get(false));
                    }
                }
            }
        }
    }
}