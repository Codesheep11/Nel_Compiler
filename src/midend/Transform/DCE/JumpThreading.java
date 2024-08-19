package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Analysis.I32RangeAnalysis;
import midend.Analysis.result.CFGinfo;
import midend.Util.CloneInfo;
import mir.Module;
import mir.*;
import midend.Analysis.result.DGinfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;

import static manager.Manager.module;

/**
 * 此过程执行 "跳跃线程" ,查看具有多个前任和多个后继的块。
 * <p>
 * 如果可以证明块的一个或多个前任总是跳转到后任之一, 我们通过复制该块的内容将边从前任转发到后任。
 * <p>
 * 发生这种情况的一个例子是如下代码:
 * if () { ... X = 4; } if (X < 3) { ... }
 **/
public class JumpThreading {

    private static I32RangeAnalysis irAnalyzer;

    private static final ArrayList<BasicBlock> workList = new ArrayList<>();

    private static final int LIMIT_BLOCK_SIZE = 1;

    private static CFGinfo cfginfo;

    public static void run(Module module) {
        AnalysisManager.runI32Range(module);
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        AnalysisManager.refreshCFG(function);
        cfginfo = AnalysisManager.getCFG(function);
        irAnalyzer = AnalysisManager.getI32Range(function);
        workList.clear();
        //考虑迭代
        workList.addAll(function.getDomTreeLayerSort());
        while (!workList.isEmpty()) {
            BasicBlock block = workList.remove(0);
            if (!block.getInstructions().isEmpty())
                runOnBlock(block);
        }
    }

    private static void collectBlock(Function function) {
        ArrayList<BasicBlock> blocks = new ArrayList<>();

        for (BasicBlock block : function.getBlocks()) {
            if (block.getInstructions().size() > LIMIT_BLOCK_SIZE) continue;
            if (cfginfo.getPredBlocks(block).size() < 2) continue;
            if (cfginfo.getSuccBlocks(block).size() < 2) continue;
            if (block.isLoopHeader()) continue;
            Instruction.Terminator term = block.getTerminator();
            if (!(term instanceof Instruction.Branch branch)) continue;
            if (!(branch.getCond() instanceof Instruction.Icmp icmp)) continue;
            blocks.add(block);
        }
    }

    public static void runOnBlock(BasicBlock block) {
//        System.out.println("JumpThread runOnBlock: " + block.getLabel());
        Instruction.Terminator term = block.getTerminator();
        if (!(term instanceof Instruction.Branch branch)) return;
        //todo: 简单版本，只考虑存在跳转指令的情况
        if (block.getInstructions().size() > LIMIT_BLOCK_SIZE) return;
        if (block.getPreBlocks().size() < 2) return;
        if (!(branch.getCond() instanceof Instruction.Icmp icmp)) return;
        ArrayList<BasicBlock> trueTargets = new ArrayList<>();
        ArrayList<BasicBlock> falseTargets = new ArrayList<>();
        for (BasicBlock pre : block.getPreBlocks()) {
            Value cond = irAnalyzer.icmpFold(icmp, pre);
            if (cond.equals(Constant.ConstantBool.get(1)))
                trueTargets.add(pre);
            else if (cond.equals(Constant.ConstantBool.get(0))) falseTargets.add(pre);
        }
        BasicBlock trueBlock = branch.getThenBlock();
        BasicBlock falseBlock = branch.getElseBlock();
        Function parentFunc = block.getParentFunction();
        if (!trueTargets.isEmpty()) {
            CloneInfo cloneInfo = new CloneInfo();
            BasicBlock blockClone = block.cloneToFunc(cloneInfo, block.getParentFunction());
            for (BasicBlock pre : trueTargets) {
//                System.out.println("go true: " + pre.getLabel());
                Instruction.Terminator preTerm = pre.getTerminator();
                preTerm.replaceTarget(block, blockClone);
            }
            blockClone.fixClone(cloneInfo);
            Instruction.Terminator cloneTerm = blockClone.getTerminator();
            cloneTerm.delete();
            new Instruction.Jump(blockClone, trueBlock);
            AnalysisManager.refreshCFG(parentFunc);
            AnalysisManager.refreshDG(parentFunc);
            //对trueBlock的Phi节点进行更新
            //已有的phi进行更新
            for (Instruction.Phi phi : trueBlock.getPhiInstructions()) {
                Value value = phi.getOptionalValue(block);
                if (value instanceof Instruction inst && inst.getParentBlock() == block)
                    value = cloneInfo.getReflectedValue(value);
                phi.addOptionalValue(blockClone, value);
            }
            //块内定义的值新建phi
            DGinfo dginfo = AnalysisManager.getDG(parentFunc);
            for (Instruction instr : block.getInstructionsSnap()) {
                if (instr instanceof Instruction.Terminator) continue;
                Instruction instrClone = (Instruction) cloneInfo.getReflectedValue(instr);
                LinkedHashMap<BasicBlock, Value> valueMap = new LinkedHashMap<>();
                for (BasicBlock pre : trueBlock.getPreBlocks()) {
                    valueMap.put(pre, instr);
                }
                valueMap.put(blockClone, instrClone);
                Instruction.Phi phi = new Instruction.Phi(trueBlock, instr.getType(), valueMap);
                phi.remove();
                trueBlock.addInstAfterPhi(phi);
                //求解要替换的指令
                ArrayList<Instruction> users = new ArrayList<>();
                for (Instruction user : instr.getUsers()) {
                    if (user.getParentBlock().equals(block) || user.getParentBlock().equals(blockClone)) {
                        if (!(user instanceof Instruction.Phi)) continue;
                    }
                    if (user.getParentBlock().equals(trueBlock)) {
                        if (user instanceof Instruction.Phi) continue;
                    }
                    BasicBlock userBlock = user.getParentBlock();
                    if (dginfo.dominate(trueBlock, userBlock) && dginfo.dominate(block, userBlock))
                        if (dginfo.getDomDepth(block) > dginfo.getDomDepth(trueBlock)) {
                            continue;
                        }
                    users.add(user);
                }
                for (Instruction user : users) {
                    user.replaceUseOfWith(instr, phi);
                }
            }
            if (!workList.contains(trueBlock)) workList.add(trueBlock);
        }
        if (!falseTargets.isEmpty()) {
            CloneInfo cloneInfo = new CloneInfo();
            BasicBlock blockClone = block.cloneToFunc(cloneInfo, block.getParentFunction());
            for (BasicBlock pre : falseTargets) {
//                System.out.println("go false: " + pre.getLabel());
                Instruction.Terminator preTerm = pre.getTerminator();
                preTerm.replaceTarget(block, blockClone);
            }
            blockClone.fixClone(cloneInfo);
            Instruction.Terminator cloneTerm = blockClone.getTerminator();
            cloneTerm.delete();
            new Instruction.Jump(blockClone, falseBlock);
            AnalysisManager.refreshCFG(parentFunc);
            AnalysisManager.refreshDG(parentFunc);
            //对falseBlock的Phi节点进行更新
            //已有的phi进行更新
            for (Instruction.Phi phi : falseBlock.getPhiInstructions()) {
                Value value = phi.getOptionalValue(block);
                if (value instanceof Instruction inst && inst.getParentBlock() == block)
                    value = cloneInfo.getReflectedValue(value);
                phi.addOptionalValue(blockClone, value);
            }
            //块内定义的值新建phi
            DGinfo dginfo = AnalysisManager.getDG(parentFunc);
            for (Instruction instr : block.getInstructionsSnap()) {
                if (instr instanceof Instruction.Terminator) continue;
                Instruction instrClone = (Instruction) cloneInfo.getReflectedValue(instr);
                LinkedHashMap<BasicBlock, Value> valueMap = new LinkedHashMap<>();
                for (BasicBlock pre : falseBlock.getPreBlocks()) {
                    valueMap.put(pre, instr);
                }
                valueMap.put(blockClone, instrClone);
                Instruction.Phi phi = new Instruction.Phi(falseBlock, instr.getType(), valueMap);
                phi.remove();
                falseBlock.addInstAfterPhi(phi);
                //求解要替换的指令
                ArrayList<Instruction> users = new ArrayList<>();
                for (Instruction user : instr.getUsers()) {
                    if (user.getParentBlock().equals(block) || user.getParentBlock().equals(blockClone)) {
                        if (!(user instanceof Instruction.Phi)) continue;
                    }
                    if (user.getParentBlock().equals(falseBlock)) {
                        if (user instanceof Instruction.Phi) continue;
                    }
                    BasicBlock userBlock = user.getParentBlock();
                    if (dginfo.dominate(falseBlock, userBlock) && dginfo.dominate(block, userBlock))
                        if (dginfo.getDomDepth(block) > dginfo.getDomDepth(falseBlock)) {
                            continue;
                        }
                    users.add(user);
                }
                for (Instruction user : users) {
                    user.replaceUseOfWith(instr, phi);
                }
            }
            if (!workList.contains(falseBlock)) workList.add(falseBlock);
        }
        if (!trueTargets.isEmpty() || !falseTargets.isEmpty()) {
            System.out.println("JumpThread: succeed " + block.getLabel());
//            Print.output(parentFunc, "debug1.txt");
            DeadCodeEliminate.run(module);
            SimplifyCFGPass.runOnFunc(parentFunc);
//            Print.output(parentFunc, "debug2.txt");
//            Print.output(block.getParentFunction(), "store.txt");
            AnalysisManager.refreshI32Range(parentFunc);
            irAnalyzer = AnalysisManager.getI32Range(parentFunc);
        }

    }
}
