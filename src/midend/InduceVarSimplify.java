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
//                //        auto matchCmp = [&](Value* v1, Value* v2) {
////            // if(!v1->is<PhiInst>())
////            //     return false;
////                const auto header = v1->getBlock();
////            if(header != trueTarget)
////                return false;
////            // handled by loop elimination
////            if(header == block)
////                return false;
////            if(!dom.dominate(header, block))
////                return false;
////            if(!isInvariant(v2, header, dom))
////                return false;
////            return true;
////        };
//                Match matchCmp = (v1, v2) -> {
//                    if (v1 instanceof Instruction inst1) {
//                        if (inst1.getParentBlock() != trueTarget)
//                            return false;
//                        if (inst1.getParentBlock() == block)
//                            return false;
//                        if (!block.getDomSet().contains(inst1.getParentBlock()))
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
//                BasicBlock header = indVar.getParentBlock();
//                final int depth = 1;
//                if (cmp == Instruction.Icmp.CondCode.SGT || cmp == Instruction.Icmp.CondCode.SGE) {
//                    if (!isIndVarMonotonic(indVar, header, true, depth))
//                        continue;
//                } else if (cmp == Instruction.Icmp.CondCode.SLT || cmp == Instruction.Icmp.CondCode.SLE) {
//                    if (!isIndVarMonotonic(indVar, header, false, depth))
//                        continue;
//                } else {
//                    continue;
//                }
//
//                if (!(isNonSideEffect(header) && isNonSideEffect(block)))
//                    continue;
//
//                Instruction headerTerminator = header.getLastInst();
//                if (headerTerminator.getInstType() != Instruction.InstType.BRANCH)
//                    continue;
//
//                Instruction.Branch headerBranch = (Instruction.Branch) headerTerminator;
//                if (headerBranch.getThenBlock() != block && headerBranch.getElseBlock() != block)
//                    continue;
//
//                BasicBlock exit = (headerBranch.getThenBlock() != block) ? headerBranch.getThenBlock() : headerBranch.getElseBlock();
//                if (exit == block || exit == header || exit == branch.getElseBlock())
//                    continue;
//
//                if (isUsedByOuter(header, exit) || isUsedByOuter(block, exit))
//                    continue;
//
////                resetTarget(branch, header, exit);
//                copyTarget(exit, header, block);
//                removePhi(block, header);
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
//    public static boolean isInvariant(Value val, BasicBlock header) {
//        if (val instanceof Instruction inst) {
//            // inst 不在header中 并且被header支配
//            return inst.getParentBlock() != header && header.getDomSet().contains(inst.getParentBlock());
//        }
//        return true;
//    }
//
//    public static boolean isIndVarMonotonic(Value indVar, BasicBlock header, boolean dir, int depth) {
//        if (indVar instanceof Instruction.Phi phi) {
//            final int maxInc = 1;
//            for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
//                if (isInvariant(entry.getValue(), header)) {
//                    continue;
//                }
//                int step = 0;
//                if (!matchAdd(entry.getValue(), indVar, step))
//                    return false;
//                if (dir) {
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
//            return isIndVarMonotonic(base, header, dir, depth - 1);
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
//        // TODO: Implement logic to match addition pattern
//        System.out.println("matchAdd to be implemented");
//        return false;  // Example implementation
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