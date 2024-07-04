package midend;

import manager.CentralControl;
import mir.*;
import mir.Module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 循环分支取消
 * <p>
 * 请在执行该优化前先执行 LIVL 优化
 */
public class LoopUnSwitching {

    private static int count = 0;

    public static void run(Module module) {
        if (!CentralControl._LUS_OPEN) return;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            for (Loop loop : function.loopInfo.TopLevelLoops)
               collectBranch(loop);
        }
    }

    private static void collectBranch(Loop loop) {
        ArrayList<Instruction.Branch> branches = new ArrayList<>();
        for (BasicBlock block : loop.nowLevelBB) {
            if (block.getLastInst() instanceof Instruction.Branch branch) {
                // 常量条件 - 可被其他优化处理
                if (branch.getCond() instanceof Constant)
                    continue;
                // 循环内依赖条件 不能提出循环

                if (loop.defValue(branch.getCond()))
                    continue;
                branches.add(branch);
            }
        }
        if (branches.isEmpty()) return;
        //demo
        unSwitching(loop, branches.get(0));
    }

    private static void unSwitching(Loop loop, Instruction.Branch branch) {
        Function parentFunction = branch.getParentBlock().getParentFunction();
        parentFunction.buildControlFlowGraph();
        BasicBlock trueBlock = branch.getThenBlock();
        BasicBlock falseBlock = branch.getElseBlock();

        LoopCloneInfo trueinfo = loop.cloneAndInfo();
        Loop trueLoop = trueinfo.cpy;
        Instruction condTrueCopy = ((Instruction) trueinfo.getReflectedValue(branch));
        BasicBlock condTrueCopyBlock = condTrueCopy.getParentBlock();
        condTrueCopy.remove();
        new Instruction.Jump(condTrueCopyBlock, (BasicBlock) trueinfo.getReflectedValue(trueBlock));
        //Note: 改造phi 以及删除不可达块 交给BuildCFG和SimplifyCFG实现
        LoopCloneInfo falseinfo = loop.cloneAndInfo();
        Loop falseLoop = falseinfo.cpy;
        Instruction condFalseCopy = ((Instruction) falseinfo.getReflectedValue(branch));
        BasicBlock condFalseCopyBlock = condFalseCopy.getParentBlock();
        condFalseCopy.remove();
        new Instruction.Jump(condFalseCopyBlock, (BasicBlock) falseinfo.getReflectedValue(falseBlock));

        BasicBlock condBlock = new BasicBlock(getNewLabel(parentFunction, "cond"), branch.getParentBlock().getParentFunction());
        new Instruction.Branch(condBlock, branch.getCond(), trueLoop.header, falseLoop.header);
        BasicBlock preHeader = loop.getPreHeader();
        trueLoop.header.getPhiInstructions().forEach(phi -> phi.changePreBlock(preHeader, condBlock));
        falseLoop.header.getPhiInstructions().forEach(phi -> phi.changePreBlock(preHeader, condBlock));
        BasicBlock exit = loop.getExit();
        for (Instruction.Phi phi : exit.getPhiInstructions()) {
            LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
            for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                if (trueinfo.containValue(entry.getKey())) {
                    newMap.put((BasicBlock) trueinfo.getReflectedValue(entry.getKey()), trueinfo.getReflectedValue(entry.getValue()));
                    newMap.put((BasicBlock) falseinfo.getReflectedValue(entry.getKey()), falseinfo.getReflectedValue(entry.getValue()));
                } else {
                    newMap.put(entry.getKey(), entry.getValue());
                }
            }
            phi.setOptionalValues(newMap);
        }
        LoopSimplifyForm.run(loop);
        parentFunction.buildControlFlowGraph();
    }

    private static String getNewLabel(Function function, String infix) {
        return function.getName() + infix + count++;
    }
}
