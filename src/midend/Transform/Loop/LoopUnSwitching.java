package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.RemoveBlocks;
import midend.Transform.DCE.SimplifyCFGPass;
import midend.Transform.GlobalCodeMotion;
import midend.Transform.GlobalValueNumbering;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 循环分支取消
 * <p>
 * 请在执行该优化前先执行 GCM/LICM 优化
 * </p>
 * Note: 该优化会改变循环的结构，执行后需要重建loop <br>
 *
 */
public class LoopUnSwitching {

    private static int count = 0;

    private static final int MAXIMUM_LINE = 200;

    public static void run(Module module) {
        handled = new HashSet<>();
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
//            for (Loop loop : function.loopInfo.TopLevelLoops)
//                collectBranch(loop);
        }
    }

    public static void runOnFunc(Function function) {
        boolean modified;
        do {
            modified = false;
            AnalysisManager.refreshCFG(function);
            LoopReBuildAndNormalize(function);
            for (var loop : function.loopInfo.TopLevelLoops) {
                modified |= unSwitching(loop);
            }
            GlobalValueNumbering.runOnFunc(function);
            GlobalCodeMotion.runOnFunc(function);
            SimplifyCFGPass.runOnFunc(function);
        } while (modified);
    }

    // NOTE: 最好不要超过6
    private static int threshold = 2;

    private static HashSet<BasicBlock> handled = new HashSet<>();

    private static boolean unSwitching(Loop loop) {
        if (handled.contains(loop.header)) return false;
        handled.add(loop.header);
        for (Loop child : loop.children) {
            if(unSwitching(child))
                return true;
        }
        threshold = 2;
        int size = loop.getSize();
        while (threshold > 0 && size * (1 << threshold) > MAXIMUM_LINE)
            threshold--;
        return collectBranch(loop);
    }

    private static boolean collectBranch(Loop loop) {
        ArrayList<Instruction.Branch> branches = new ArrayList<>();
        for (BasicBlock block : loop.nowLevelBB) {
            if (block.getInstructions().isEmpty()) {
                System.out.println("empty block " + block.getLabel());
            }
            if (block.getLastInst() instanceof Instruction.Branch branch) {
                // 常量条件 - 可被其他优化处理
                if (branch.getCond() instanceof Constant)
                    continue;
                // 循环内依赖条件 不能提出循环
                // 前置条件为执行过 GCM 优化
                if (loop.defValue(branch.getCond()))
                    continue;
                branches.add(branch);
                if (branches.size() >= threshold)
                    break;
            }
        }
        if (branches.isEmpty()) return false;
        //demo
        unSwitching(loop, branches);
        return true;
    }

    private static void unSwitching(Loop loop, ArrayList<Instruction.Branch> branches) {
        Instruction.Branch branch = branches.get(0);
        Function parentFunction = branch.getParentBlock().getParentFunction();
        BasicBlock oldPreHeader = loop.getPreHeader();
        AnalysisManager.refreshCFG(parentFunction);
        ArrayList<BasicBlock> trueBlocks = new ArrayList<>();
        ArrayList<BasicBlock> falseBlocks = new ArrayList<>();

        for (var br : branches) {
            trueBlocks.add(br.getThenBlock());
            falseBlocks.add(br.getElseBlock());
        }

        ArrayList<BasicBlock> condBlocks = new ArrayList<>();
        for (int i = 1; i < (1 << branches.size()); ++i)
            condBlocks.add(new BasicBlock(getNewLabel(parentFunction, "unswitch"), parentFunction));

        ArrayList<LoopCloneInfo> infos = new ArrayList<>();
        for (int i = (1 << branches.size()); i < (1 << (branches.size() + 1)); ++i) {
            LoopCloneInfo info = loop.cloneAndInfo();
            infos.add(info);
            Loop newLoop = info.cpy;
            for (int j = 0; j < branches.size(); ++j) {
                Instruction cond = ((Instruction) info.getReflectedValue(branches.get(j)));
                BasicBlock condBlock = cond.getParentBlock();
                cond.delete();
                if ((i & (1 << (branches.size() - 1 - j))) == 0) {
                    new Instruction.Jump(condBlock, (BasicBlock) info.getReflectedValue(trueBlocks.get(j)));
                }
                else {
                    new Instruction.Jump(condBlock, (BasicBlock) info.getReflectedValue(falseBlocks.get(j)));
                }
            }
            int finalI = i;
            newLoop.header.getPhiInstructions().forEach(phi -> phi.changePreBlock(oldPreHeader, condBlocks.get((finalI >> 1) - 1)));

            condBlocks.add(newLoop.header);
        }

        for (int i = 1; i < (1 << branches.size()); ++i) {
            var lson = condBlocks.get((i << 1) - 1);
            var rson = condBlocks.get(i << 1);
            int k = Integer.toBinaryString(i).length();
            new Instruction.Branch(condBlocks.get(i - 1), branches.get(k - 1).getCond(), lson, rson);
        }

        // modify preheader
        oldPreHeader.getTerminator().replaceTarget(loop.header, condBlocks.get(0));

        // modify exits
        for (BasicBlock exit : loop.exits) {
            for (Instruction.Phi phi : exit.getPhiInstructions()) {
                LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
                for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                    if (infos.get(0).containValue(entry.getKey())) {
                        if (infos.get(0).containValue(entry.getValue())) {
                            infos.forEach(info ->
                                    newMap.put((BasicBlock) info.getReflectedValue(entry.getKey()), info.getReflectedValue(entry.getValue())));
                        }
                        else {
                            infos.forEach(info ->
                                    newMap.put((BasicBlock) info.getReflectedValue(entry.getKey()), entry.getValue()));
                        }
                    }
                    else {
                        newMap.put(entry.getKey(), entry.getValue());
                    }
                }
                phi.setOptionalValues(newMap);
            }
        }

        RemoveBlocks.runOnFunc(parentFunction);
    }

    //demo
    private static void unSwitching(Loop loop, Instruction.Branch branch) {
        Function parentFunction = branch.getParentBlock().getParentFunction();
        parentFunction.buildControlFlowGraph();
        BasicBlock trueBlock = branch.getThenBlock();
        BasicBlock falseBlock = branch.getElseBlock();

        // copy true loop
        LoopCloneInfo trueinfo = loop.cloneAndInfo();
        Loop trueLoop = trueinfo.cpy;
        Instruction condTrueCopy = ((Instruction) trueinfo.getReflectedValue(branch));
        BasicBlock condTrueCopyBlock = condTrueCopy.getParentBlock();
        condTrueCopy.delete();
        new Instruction.Jump(condTrueCopyBlock, (BasicBlock) trueinfo.getReflectedValue(trueBlock));
        //Note: 改造phi 以及删除不可达块 交给BuildCFG和SimplifyCFG实现
        // copy false loop
        LoopCloneInfo falseinfo = loop.cloneAndInfo();
        Loop falseLoop = falseinfo.cpy;
        Instruction condFalseCopy = ((Instruction) falseinfo.getReflectedValue(branch));
        BasicBlock condFalseCopyBlock = condFalseCopy.getParentBlock();
        condFalseCopy.delete();
        new Instruction.Jump(condFalseCopyBlock, (BasicBlock) falseinfo.getReflectedValue(falseBlock));

        // create cond block
        BasicBlock condBlock = new BasicBlock(getNewLabel(parentFunction, "unswitch"), parentFunction);
        new Instruction.Branch(condBlock, branch.getCond(), trueLoop.header, falseLoop.header);
        BasicBlock preHeader = loop.getPreHeader();
        trueLoop.header.getPhiInstructions().forEach(phi -> phi.changePreBlock(preHeader, condBlock));
        falseLoop.header.getPhiInstructions().forEach(phi -> phi.changePreBlock(preHeader, condBlock));

        // modify preheader
        preHeader.getTerminator().replaceTarget(loop.header, condBlock);

        for (BasicBlock exit : loop.exits) {
            for (Instruction.Phi phi : exit.getPhiInstructions()) {
                LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
                for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                    if (trueinfo.containValue(entry.getKey())) {
                        if (trueinfo.containValue(entry.getValue())) {
                            newMap.put((BasicBlock) trueinfo.getReflectedValue(entry.getKey()), trueinfo.getReflectedValue(entry.getValue()));
                            newMap.put((BasicBlock) falseinfo.getReflectedValue(entry.getKey()), falseinfo.getReflectedValue(entry.getValue()));
                        }
                        else {
                            newMap.put((BasicBlock) trueinfo.getReflectedValue(entry.getKey()), entry.getValue());
                            newMap.put((BasicBlock) falseinfo.getReflectedValue(entry.getKey()), entry.getValue());
                        }
                    }
                    else {
                        newMap.put(entry.getKey(), entry.getValue());
                    }
                }
                phi.setOptionalValues(newMap);
            }
        }
        RemoveBlocks.runOnFunc(parentFunction);
    }

    private static void LoopReBuildAndNormalize(Function func) {
        LCSSA.removeOnFunc(func);
        LoopInfo.runOnFunc(func);
        LoopSimplifyForm.runOnFunc(func);
        LCSSA.runOnFunc(func);
    }

    private static String getNewLabel(Function function, String infix) {
        return function.getName() + infix + count++;
    }
}
