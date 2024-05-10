//package midend;
//
//import mir.*;
//import mir.Module;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.HashSet;
//
//public class RemovePhi {
//
//    private static Collection<Function> functions;
//
//    public static void run(Module module) {
//        functions = module.getFuncSet();
//        RemovePhiAddPCopy();
//        ReplacePCopy();
//    }
//
//    private static void RemovePhiAddPCopy() {
//        for (Function function: functions) {
//            if (function.isExternal()) {
//                continue;
//            }
//            removeFuncPhi(function);
//        }
//    }
//
//    private static void ReplacePCopy() {
//        for (Function function: functions) {
//            if (function.isExternal()) {
//                continue;
//            }
//            replacePCopyForFunc(function);
//        }
//    }
//
//
//    private static void removeFuncPhi(Function function) {
//
//        //将所有对phi的赋值更改为PhiCopy
//        for (BasicBlock bb:
//                function.getBlocks()) {
//            if (!(bb.getFirstInst() instanceof Instruction.Phi)) {
//                continue;
//            }
//            ArrayList<BasicBlock> pres = new ArrayList<>();
//            for (BasicBlock block:bb.getPreBlocks()) {
//                pres.add(block);
//            }
//            ArrayList<Instruction.PhiCopy> PhiCopys = new ArrayList<>();
//
//            for (int i = 0; i < pres.size(); i++) {
//
//                BasicBlock incomeBB = pres.get(i);
//
//                if (incomeBB.getSucBlocks().size() > 1) {
//                    BasicBlock mid = new BasicBlock(incomeBB.getLabel()+"_to_"+bb.getLabel(), function);
//                    Instruction.PhiCopy phiCopy = new Instruction.PhiCopy(mid, new ArrayList<>(), new ArrayList<>());
//                    PhiCopys.add(phiCopy);
//                    addMidBB(incomeBB, mid, bb);
//                } else {
//                    Instruction endInstr = incomeBB.getLastInst();
//                    Instruction.PhiCopy phiCopy = new Instruction.PhiCopy(incomeBB, new ArrayList<>(), new ArrayList<>());
//                    phiCopy.remove();
//                    incomeBB.getInstructions().insertBefore(phiCopy, endInstr);
//                    PhiCopys.add(phiCopy);
//                }
//
//            }
//
//            Instruction inst = bb.getFirstInst();
//            while (inst instanceof Instruction.Phi) {
//                for (int i = 0; i < ((Instruction.Phi) inst).getSize(); i++) {
//                    PhiCopys.get(i).add(inst, ((Instruction.Phi) inst).getOptionalValue(i));
//                }
//                if (!(inst.getNext() instanceof Instruction)) {
//                    break;
//                }
//                inst = (Instruction) inst.getNext();
//            }
//
//
//            //todo: 是否应该删除phi？，phi可以留下作为占位适应后端？
////            inst = bb.getFirstInst();
////            while (inst instanceof Instruction.Phi) {
////                inst.remove();
////                if (inst.getNext() instanceof Instruction)
////                    inst = (Instruction) inst.getNext();
////                else
////                    break;
////            }
//        }
//    }
//
//    private static void addMidBB(BasicBlock src, BasicBlock mid, BasicBlock target) {
//        src.getSucBlocks().remove(target);
//        src.getSucBlocks().add(mid);
//        mid.getPreBlocks().add(src);
//        mid.getSucBlocks().add(target);
//        target.getPreBlocks().remove(src);
//        target.getPreBlocks().add(mid);
//
//        Instruction inst = src.getLastInst();
//        assert inst instanceof Instruction.Branch;
//
//        inst.replaceUseOfWith(target, mid);
//        new Instruction.Jump(target, mid);
//
//    }
//
//    private static void replacePCopyForFunc(Function function) {
//
//        for (BasicBlock bb:
//                function.getBlocks()) {
//            ArrayList<Instruction> moves = new ArrayList<>();
//            ArrayList<Instruction> PhiCopys = new ArrayList<>();
//            for (Instruction inst:
//                    bb.getInstructions()) {
//                if (!(inst instanceof Instruction.PhiCopy)) {
//                    continue;
//                }
//                PhiCopys.add(inst);
//                ArrayList<Value> targets = ((Instruction.PhiCopy) inst).getLHS();
//                ArrayList<Value> srcs = ((Instruction.PhiCopy) inst).getRHS();
//
//                HashSet<String> tagNameSet = new HashSet<>();
//                HashSet<String> srcNameSet = new HashSet<>();
//
//                removeUndefCopy(targets, srcs, tagNameSet, srcNameSet);
//
//
//                //将PhiCopy替换为move
//                while (!checkPhiCopy(targets, srcs)) {
//                    boolean temp = false;
//                    for (int i = 0; i < targets.size(); i++) {
//                        String tagName = targets.get(i).getDescriptor();
//                        if (!srcNameSet.contains(tagName)) {
//                            Instruction move = new Instruction.Move(bb, targets.get(i).getType(), targets.get(i), srcs.get(i));
//                            moves.add(move);
//
//                            tagNameSet.remove(targets.get(i).getDescriptor());
//                            srcNameSet.remove(srcs.get(i).getDescriptor());
//
//                            targets.remove(i);
//                            srcs.remove(i);
//
//                            temp = true;
//                            break;
//                        }
//                    }
//
//                    //处理undef的phi入口
//                    if (!temp) {
//                        for (int i = 0; i < targets.size(); i++) {
//                            String srcName = srcs.get(i).getDescriptor();
//                            Value src = srcs.get(i);
//                            Value target = targets.get(i);
//                            if (!srcs.get(i).getDescriptor().equals(targets.get(i).getDescriptor())) {
//                                Constant newSrc = GlobalVariable.getUndef(target.getType());
//                                Instruction move = new Instruction.Move(bb, target.getType(), newSrc, src);
//                                moves.add(move);
//                                srcs.set(i, newSrc);
//
//                                srcNameSet.remove(srcName);
//                                srcNameSet.add(move.getDescriptor());
//                            }
//                        }
//                    }
//
//                }
//
//            }
//            for (Instruction inst: PhiCopys) {
//                inst.remove();
//            }
//            for (Instruction inst: moves) {
//                inst.remove();;
//                bb.getInstructions().insertBefore(inst, bb.getLastInst());
//            }
//
//        }
//    }
//
//    private static boolean checkPhiCopy(ArrayList<Value> targets, ArrayList<Value> srcs) {
//        for (int i = 0; i < targets.size(); i++) {
//            if (!targets.get(i).getDescriptor().equals(srcs.get(i).getDescriptor())) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    private static void removeUndefCopy(ArrayList<Value> targets, ArrayList<Value> srcs,
//                                 HashSet<String> tagNames, HashSet<String> srcNames) {
//        ArrayList<Value> tempTag = new ArrayList<>();
//        ArrayList<Value> tempSrc = new ArrayList<>();
//        for (int i = 0; i < targets.size(); i++) {
//
//            if (GlobalVariable.undefTable.containsKey(srcs.get(i))) {
//                continue;
//            }
//            tempTag.add(targets.get(i));
//            tempSrc.add(srcs.get(i));
//        }
//        targets.clear();
//        srcs.clear();
//        targets.addAll(tempTag);
//        srcs.addAll(tempSrc);
//
//
//        for (Value value: targets) {
//            tagNames.add(value.getDescriptor());
//        }
//        for (Value value: srcs) {
//            srcNames.add(value.getDescriptor());
//        }
//
//    }
//}
