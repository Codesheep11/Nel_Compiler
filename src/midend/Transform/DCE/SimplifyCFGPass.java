package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import mir.*;
import mir.Module;

import java.util.*;

/**
 * 删除没有前驱的基本块
 * 如果一个基本块只有一个前驱，并且该前驱只有一个后继，则将该基本块合并到其前驱中
 * 对于只有一个前驱的基本块，删除其PHI节点
 * 删除仅包含无条件分支的基本块。
 */
public class SimplifyCFGPass {

    public static boolean run(Module module) {
        boolean modified = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            modified |= runOnFunc(function);
        }
        return modified;
    }


    public static boolean runOnFunc(Function function) {
        boolean modified = false;
        boolean changed;
        do {
            changed = false;
            changed |= Br2Jump(function);
            RemoveBlocks.runOnFunc(function);
            AnalysisManager.refreshDG(function);
            changed |= MergeBlocks(function);
            RemoveBlocks.runOnFunc(function);
            changed |= ChangeTarget(function);
            RemoveBlocks.runOnFunc(function);
            changed |= MergePhi(function);
            modified |= changed;
        } while (changed);
        return modified;
//        Print.outputLLVM(function, "debug2.txt");
    }

    private static boolean MergeBlocks(Function function) {
        HashMap<BasicBlock, ArrayList<BasicBlock>> mergeMap = new HashMap<>();
        HashSet<BasicBlock> mergeBlocks = new HashSet<>();
        ArrayList<BasicBlock> domSort = function.getDomTreeLayerSort();
        //支配树层次遍历保证了首先一定访问到头部
        for (BasicBlock block : domSort) {
            if (mergeBlocks.contains(block)) continue;
            if (block.getSucBlocks().size() == 1) {
                BasicBlock cur = block.getSucBlocks().get(0);
                ArrayList<BasicBlock> merges = new ArrayList<>();
                while (cur.getPreBlocks().size() == 1
                        && cur.getPhiInstructions().isEmpty()) {
                    mergeBlocks.add(cur);
                    merges.add(cur);
                    if (cur.getSucBlocks().size() == 1) cur = cur.getSucBlocks().get(0);
                    else break;
                }
                if (!merges.isEmpty()) mergeMap.put(block, merges);
            }
        }
//        Print.outputLLVM(function, "store.txt");
        for (BasicBlock first : mergeMap.keySet()) {
            first.getLastInst().delete();
            for (BasicBlock block : mergeMap.get(first)) {
                Iterator<Instruction> iterator = block.getInstructions().iterator();
                while (iterator.hasNext()) {
                    Instruction instr = iterator.next();
                    if (instr instanceof Instruction.Phi) {
                        throw new RuntimeException("LCSSA should not be here");
                    }
                    if (instr instanceof Instruction.Terminator) break;
                    iterator.remove();
                    first.addInstLast(instr);
                    instr.setParentBlock(first);
                }
            }
            for (int i = 0; i < mergeMap.get(first).size(); i++) {
                BasicBlock block = mergeMap.get(first).get(i);
                if (i == mergeMap.get(first).size() - 1) {
                    Instruction term = block.getLastInst();
                    term.remove();
                    first.addInstLast(term);
                    term.setParentBlock(first);
                    for (BasicBlock suc : block.getSucBlocks()) {
                        for (Instruction phi : suc.getInstructions()) {
                            if (phi instanceof Instruction.Phi) ((Instruction.Phi) phi).changePreBlock(block, first);
                            else break;
                        }
                    }
                }
                else block.getLastInst().delete();
            }
        }
        return !mergeMap.isEmpty();
    }

    private static boolean Br2Jump(Function function) {
        boolean modified = false;
        for (BasicBlock block : function.getBlocks()) {
            Instruction.Terminator term = (Instruction.Terminator) block.getLastInst();
            if (term instanceof Instruction.Branch br) {
                if (br.getElseBlock().equals(br.getThenBlock())) {
                    new Instruction.Jump(block, br.getElseBlock());
                    br.delete();
                    modified = true;
                }
                if (br.getCond() instanceof Constant.ConstantBool) {
                    if (((Constant.ConstantBool) br.getCond()).isZero()) {
                        new Instruction.Jump(block, br.getElseBlock());
                        br.delete();
                        modified = true;
                    }
                    else {
                        new Instruction.Jump(block, br.getThenBlock());
                        br.delete();
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    private static boolean ChangeTarget(Function function) {
        ArrayList<BasicBlock> onlyJumpBlocks = new ArrayList<>();
        for (BasicBlock block : function.getBlocks()) {
            if (block.getFirstInst() instanceof Instruction.Jump) {
                BasicBlock suc = block.getSucBlocks().get(0);
                if (suc.equals(block)) continue;
                if (!(suc.getFirstInst() instanceof Instruction.Phi) && !block.getPreBlocks().isEmpty()) {
                    onlyJumpBlocks.add(block);
                }
            }
        }
        ListIterator<BasicBlock> iterator = onlyJumpBlocks.listIterator();
        while (iterator.hasNext()) {
            BasicBlock onlyJumpBlock = iterator.next();
            BasicBlock suc = onlyJumpBlock.getSucBlocks().get(0);
            for (BasicBlock pre : onlyJumpBlock.getPreBlocks()) {
                Instruction.Terminator term = (Instruction.Terminator) pre.getLastInst();
                term.replaceTarget(onlyJumpBlock, suc);
                if (term instanceof Instruction.Branch br) {
                    if (br.getElseBlock().equals(br.getThenBlock())) {
                        new Instruction.Jump(pre, br.getElseBlock());
                        br.delete();
                        if (pre.getFirstInst() instanceof Instruction.Jump) iterator.add(pre);
                    }
                }
            }
        }
        return !onlyJumpBlocks.isEmpty();
    }

    private static boolean MergePhi(Function function) {
        return false;
    }

}
