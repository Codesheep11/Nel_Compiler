//package midend;
//
//import mir.*;
//
//import java.util.*;
//
///**
// * Induction Variable Simplification
// * <p>
// *
// * @author Srchycz
// */
//public class InduceVarSimplify {
//
//    @FunctionalInterface
//    interface Match {
//        boolean match(Value v1, Value v2);
//    }
//
//    public boolean run(Function func) {
//        func.buildDominanceGraph();
//        boolean modified = false;
//
//        for (BasicBlock block : func.getBlocks()) {
//            Instruction.Terminator terminator = block.getTerminator();
//            if (terminator instanceof Instruction.Branch branch) {
//                if (!(branch.getCond() instanceof Instruction.Icmp icmp))
//                    continue;
//                BasicBlock trueTarget = branch.getThenBlock();
//                if (trueTarget == branch.getElseBlock())
//                    continue;
//
//                Instruction.Icmp.CondCode cmp = icmp.getCondCode();
//                Instruction indVar;
//                Value x = icmp.getSrc1(), y = icmp.getSrc2();
//
//                // 用于判断 (v1, v2) 是否符合 (indvar, n)
//                Match matchCmp = (v1, v2) -> {
//                    if (v1 instanceof Instruction inst1) {
//                        // 必须经过loopRotate?
//                        if (inst1.getParentBlock() != trueTarget)
//                            return false;
//                        if (inst1.getParentBlock() == block)
//                            return false;
//                        // FIXME: 似乎冗余？
//                        if (!inst1.getParentBlock().dominates(block))
//                            return false;
//                        return isInvariant(v2, inst1.getParentBlock());
//                    }
//                    return false;
//                };
//
//                if (matchCmp.match(x, y)) {
//                    indVar = (Instruction) x;
//                } else if (matchCmp.match(y, x)) {
//                    indVar = (Instruction) y;
//                    cmp = cmp.inverse();
//                } else {
//                    continue;
//                }
//
//                BasicBlock indvarBlock = indVar.getParentBlock();
//                final int depth = 1;
//                if (cmp == Instruction.Icmp.CondCode.SGT || cmp == Instruction.Icmp.CondCode.SGE) {
//                    if (!isIndVarMonotonic(indVar, indvarBlock, true, depth))
//                        continue;
//                } else if (cmp == Instruction.Icmp.CondCode.SLT || cmp == Instruction.Icmp.CondCode.SLE) {
//                    if (!isIndVarMonotonic(indVar, indvarBlock, false, depth))
//                        continue;
//                } else {
//                    continue;
//                }
//
//                if (!(isNonSideEffect(indvarBlock) && isNonSideEffect(block)))
//                    continue;
//
//                Instruction headerTerminator = indvarBlock.getLastInst();
//                if (headerTerminator.getInstType() != Instruction.InstType.BRANCH)
//                    continue;
//
//                Instruction.Branch headerBranch = (Instruction.Branch) headerTerminator;
//                if (headerBranch.getThenBlock() != block && headerBranch.getElseBlock() != block)
//                    continue;
//
//                BasicBlock exit = (headerBranch.getThenBlock() != block) ? headerBranch.getThenBlock() : headerBranch.getElseBlock();
//                if (exit == block || exit == indvarBlock || exit == branch.getElseBlock())
//                    continue;
//
//                if (isUsedByOuter(indvarBlock, exit) || isUsedByOuter(block, exit))
//                    continue;
//
////                resetTarget(branch, indvarBlock, exit);
//                copyTarget(exit, indvarBlock, block);
//                removePhi(block, indvarBlock);
//
////                final double earlyExitProb = 0.1;
////                branch.swapTargets();
////                branch.updateBranchProb(1.0 - earlyExitProb);
////                CompareInst cond = (CompareInst) branch.getOperand(0);
////                cond.setOp(getInvertedOp(cond.getOp()));
//
//                modified = true;
//            }
//        }
//
//        return modified;
//    }
//
//    public static boolean isInvariant(Value val, BasicBlock block) {
//        if (val instanceof Instruction inst) {
//            // inst 被 block 严格支配
//            return inst.getParentBlock() != block && block.getDomSet().contains(inst.getParentBlock());
//        }
//        return true;
//    }
//
//    /**
//     * 判断归纳变量是否单调
//     * 目前只能判断加法
//     */
//    private static boolean isIndVarMonotonic(Value indVar, BasicBlock block, boolean isIncrease, int depth) {
//        if (indVar instanceof Instruction.Phi phi) {
//            final int maxInc = 1;
//            for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
//                if (isInvariant(entry.getValue(), block)) {
//                    continue;
//                }
//                int step = 0;
//                if (entry.getValue() instanceof Instruction.Add add) {
//                    Value op1 = add.getOperand_1(), op2 = add.getOperand_2();
//                    if (op1 instanceof Constant.ConstantInt constant) {
//                        step = constant.getIntValue();
//                        indVar = op2;
//                    } else if (op2 instanceof Constant.ConstantInt constant) {
//                        step = constant.getIntValue();
//                        indVar = op1;
//                    }
//                } else continue;
//
//                if (isIncrease) {
//                    if (step <= 0)
//                        return false;
//                    if (step <= maxInc)
//                        continue;
//                } else {
//                    if (step >= 0)
//                        return false;
//                    if (step >= -maxInc)
//                        continue;
//                }
//                return false;
//            }
//            return true;
//        }
//        if (depth < 0)
//            return false;
//        Value base;
//        int step;
//        if (matchAdd(indVar, base, step)) {
//            return isIndVarMonotonic(base, block, isIncrease, depth - 1);
//        }
//        return false;
//    }
//
//    public static boolean isNonSideEffect(BasicBlock block) {
//        for (Instruction inst : block.getInstructions()) {
//            if (inst.isTerminator())
//                continue;
//            if (!inst.isNoSideEffect() || inst.getInstType() == Instruction.InstType.LOAD) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    public static boolean isUsedByOuter(BasicBlock block, BasicBlock exit) {
//        for (Instruction inst : block.getInstructions()) {
//            for (Use use : inst.getUses()) {
//                User user = use.getUser();
//                if (user instanceof Instruction userInst) {
//                    if (userInst.getParentBlock().getDomSet().contains(exit))
//                        return true;
//                    if (user instanceof Instruction.Phi phi) {
//                        for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
//                            if (entry.getValue() == inst) {
//                                if (entry.getKey().getDomSet().contains(exit))
//                                    return true;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//    // Utility methods for matching, pattern recognition, and transformations
//    private static boolean matchAdd(Value value, Value base, int step) {
//        if (value instanceof Instruction.Add add) {
//            Value op1 = add.getOperand_1(), op2 = add.getOperand_2();
//            if (op1 instanceof Constant.ConstantInt constant) {
//                step = constant.getIntValue();
//                base = op2;
//                return true;
//            } else if (op2 instanceof Constant.ConstantInt constant) {
//                step = constant.getIntValue();
//                base = op1;
//                return true;
//            }
//        }
//        return false;
//    }
//
//    private static void copyTarget(BasicBlock exit, BasicBlock header, BasicBlock block) {
//        // TODO: Implement logic to copy target
//        System.out.println("copyTarget to be implemented");
//    }
//
//    private static void removePhi(BasicBlock block, BasicBlock header) {
//        // TODO: Implement logic to remove phi
//        System.out.println("removePhi to be implemented");
//    }
//}