//package midend;
//
//import mir.*;
//import mir.Module;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedHashMap;
//
///**
// * 循环反转
// * <p>
// * 用于辅助 LoopUnroll 的工作 <br>
// * 在规范化规约变量之后进行indvars 效果最佳 <br>
// * <p>
// * 不是所有的循环都适合循环反转+展开，已增设评估函数
// *
// * @author Srchycz
// */
//public class LoopRotate {
//
//    private static int count = 0;
//
////    public static void run(Loop loop) {
////        for (Loop child : loop.children) {
////            run(child);
////        }
////        setLoopGuard(loop);
////        simplifyLatch(loop);
////        CanonicalizeExits(loop);
////    }
//
////    private static void setLoopGuard(Loop loop) {
////        if (loop.cond instanceof Constant) {
////            return;
////        }
////        if (loop.cond instanceof Instruction inst) {
////            BasicBlock guard = new BasicBlock(getNewLabel(loop.header.getParentFunction(), "guard"), loop.header.getParentFunction());
////            inst.cloneToBB(guard);
////            new Instruction.Branch(guard, inst, loop.header, loop.getExit());
////            loop.enterings.forEach(entering -> entering.replaceSucc(loop.header, guard));
////            loop.enterings.clear();
////            loop.enterings.add(guard);
////        }
////    }
//    // TODO: cmmc 源码中设置了该参数, 暂不清楚作用
////    private static final int MAX_ROTATE_COUNT = 8;
//
//
//    private static boolean run(Loop loop) {
//        Function func = loop.header.getParentFunction();
//        boolean modified = false;
//        if (!canBeRotated(loop) || loop.header == loop.getLatch())
//            return modified;
//
//        BasicBlock loopBody = null;
//        ArrayList<BasicBlock> successorsHeader = loop.header.getSucBlocks();
//        for (BasicBlock b : successorsHeader) {
//            if (loop.header.dominates(b) && b.dominates(loop.getLatch())) {
//                loopBody = b;
//            }
//        }
//        if (loopBody == null)
//            return modified;
//
////        Set<BasicBlock> body = new HashSet<>();
////        if (!collectLoopBody(loop.header, loop.latch, dom, cfg, body, true, false))
////            continue;
//
//        BasicBlock exit = successorsHeader.get(0) == loopBody ? successorsHeader.get(1) : successorsHeader.get(0);
////        if (body.contains(exiting))
////            continue;
//
//
//        if (loopBody.getPreBlocks().size() != 1 || loopBody.getInstructions().get(0).getInstType() != Instruction.InstType.PHI) {
//            BasicBlock newPhiLoc = new BasicBlock(getNewLabel(func, "newbody"), func);
//            Instruction.Branch term = new Instruction.Branch(newPhiLoc, loopBody, exit);
//
//            loop.header.getTerminator().replaceSucc(loopBody, newPhiLoc);
//            for (Instruction.Phi phi : loopBody.getPhiInstructions()) {
//                phi.changePreBlock(loop.header, newPhiLoc);
//            }
//            loopBody = newPhiLoc;
//        }
//
//        BasicBlock indirectBlock = null;
//        if (exit.getPreBlocks().size() != 1 || exit.getInstructions().get(0).getInstType() == Instruction.InstType.PHI) {
//            indirectBlock = new BasicBlock(getNewLabel(func, "newbody"), func);
//            loop.header.getTerminator().replaceSucc(exit, indirectBlock);
//        }
//
//        HashMap<Value, Instruction.Phi> replace = new HashMap<>();
//        loop.getLatch().getLastInst().delete();
//        HashSet<Instruction> oldInsts = new HashSet<>();
//        HashSet<Instruction> newInsts = new HashSet<>();
//        for (BasicBlock b : body) {
//            if (b != loop.header) {
//                oldInsts.addAll(b.getInstructions());
//            }
//        }
//
//        for (Instruction inst : loop.header.getInstructions()) {
//            if (inst.canbeOperand()) {
//                Instruction.Phi phi = new Instruction.Phi(loopBody, inst.getType(), new LinkedHashMap<>());
//                replace.put(inst, phi);
//                phi.insertBefore(loopBody, loopBody.getInstructions().size());
//            }
//        }
//
//        for (Instruction inst : loop.header.getInstructions()) {
//            if (inst instanceof Instruction.Phi phi) {
//                Value val = phi.getOptionalValue(loop.getLatch());
//                phi.removeIncomingValue(loop.latch);
//                PhiInst newPhi = replace.get(inst);
//                newPhi.addIncoming(loop.header, inst);
//                if (replace.containsKey(val)) {
//                    val = replace.get(val);
//                }
//                newPhi.addIncoming(loop.latch, val);
//            } else {
//                Instruction newInst = inst.clone();
//                newInst.insertBefore(loop.latch, loop.latch.getInstructions().size());
//                if (inst.canBeOperand()) {
//                    PhiInst phi = replace.get(inst);
//                    phi.addIncoming(loop.header, inst);
//                    phi.addIncoming(loop.latch, newInst);
//                }
//                newInsts.add(newInst);
//            }
//        }
//
//        for (Instruction inst : loop.header.getInstructions()) {
//            for (ValueUser user : inst.getUsers()) {
//                if (oldInsts.contains(user)) {
//                    user.resetValue(replace.get(inst));
//                } else if (newInsts.contains(user)) {
//                    user.resetValue(replace.get(inst).getIncomingValue(loop.latch));
//                }
//            }
//        }
//
//        for (Instruction inst : loop.header.getInstructions()) {
//            boolean usedByOuter = false;
//            for (ValueUser user : inst.getUsers()) {
//                BasicBlock BasicBlock = user.getBlock();
//                if (BasicBlock == loop.header && user.getInstID() == InstructionID.PHI) {
//                    for (Incoming incoming : ((PhiInst) user).getIncomingValues()) {
//                        if (incoming.getValue() != user) continue;
//                        if (!body.contains(incoming.getBlock())) {
//                            usedByOuter = true;
//                            break;
//                        }
//                    }
//                } else {
//                    if (!body.contains(BasicBlock)) {
//                        usedByOuter = true;
//                        break;
//                    }
//                }
//            }
//
//            if (!usedByOuter) continue;
//
//            PhiInst phi = new PhiInst(inst.getType());
//            for (ValueUser user : inst.getUsers()) {
//                BasicBlock BasicBlock = user.getBlock();
//                if (!body.contains(BasicBlock)) {
//                    user.resetValue(phi);
//                } else if (BasicBlock == loop.header && user.getInstID() == InstructionID.PHI) {
//                    for (Incoming incoming : ((PhiInst) user).getIncomingValues()) {
//                        if (incoming.getValue() != user) continue;
//                        if (!body.contains(incoming.getBlock())) {
//                            user.resetValue(phi);
//                            break;
//                        }
//                    }
//                }
//            }
//
//            phi.addIncoming(loop.header, inst);
//            phi.addIncoming(loop.latch, replace.get(inst).getIncomingValue(loop.latch));
//            if (indirectBlock != null) {
//                phi.insertBefore(indirectBlock, indirectBlock.getInstructions().size());
//            } else {
//                phi.insertBefore(exit, exit.getInstructions().size());
//            }
//        }
//
//        if (indirectBlock != null) {
//            BranchInst terminator = new BranchInst(exit);
//            terminator.insertBefore(indirectBlock, indirectBlock.getInstructions().size());
//            func.getBlocks().add(indirectBlock);
//
//            for (Instruction phi : exit.getInstructions()) {
//                if (phi.getInstID() == InstructionID.PHI) {
//                    ((PhiInst) phi).replaceSource(loop.header, indirectBlock);
//                }
//            }
//        }
//
//        modified = true;
//        return modified;
//    }
//
//
//    /**
//     * 判断循环是否可以被反转 <br>
//     * 前置条件:
//     * <p>
//     * 1. 建立过 CFG <br>
//     * 2. 循环为简化形式
//     * </p>
//     * @param loop 需要判断的循环
//     * @return boolean
//     */
//    public static boolean canBeRotated(Loop loop) {
//        if (loop.header.hasCall())
//            return false;
//
//        ArrayList<BasicBlock> pre =loop.getLatch().getPreBlocks();
//        if (!(pre.size() == 1 && pre.get(0) == loop.header))
//            return false;
//
//        ArrayList<BasicBlock> succ = loop.getLatch().getSucBlocks();
//        if (!(succ.size() == 1 && succ.get(0) == loop.header))
//            return false;
//
//        return loop.header.getSucBlocks().size() == 2;
//    }
//
//    private static boolean cleanupPhi(Function func) {
//        boolean modified = false;
//        System.out.println("cleanupPhi to be implemented");
////        for (BasicBlock BasicBlock : func.getBlocks()) {
////            List<Instruction> instructions = BasicBlock.getInstructions();
////            Iterator<Instruction> it = instructions.iterator();
////            while (it.hasNext()) {
////                Instruction inst = it.next();
////                if (inst instanceof PhiInst phi) {
////                    PhiInst phi = (PhiInst) inst;
////                    if (phi.getIncomingValues().size() == 1) {
////                        inst.replaceWith(phi.getIncomingValue(0).getValue());
////                        it.remove();
////                    }
////                } else {
////                    break;
////                }
////            }
////        }
//        return modified;
//    }
//
//
//    private static String getNewLabel(Function function, String infix) {
//        return function.getName() + infix + count++;
//    }
//}
