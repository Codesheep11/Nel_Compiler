package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Analysis.MemDepAnalysis;
import midend.Analysis.PointerBaseAnalysis;
import midend.Transform.DCE.SimplifyCFGPass;
import mir.Module;
import mir.*;

import java.util.ArrayList;

/**
 * 提取特定循环: 无副作用，退出条件简单，返回值不依赖于父循环
 */
public class CertainLoopExtract {

    private static boolean changed = false;

    public static boolean run(Module module) {
        changed = false;
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
        return changed;
    }

    public static void runOnFunc(Function function) {
        boolean modified;
        do {
            modified = false;
            AnalysisManager.refreshCFG(function);
            LoopReBuildAndNormalize(function);
            PointerBaseAnalysis.runOnFunc(function);
            for (Loop loop : function.loopInfo.TopLevelLoops) {
                if (tryExtractLoop(loop)) {
                    modified = true;
                    changed = true;
                    break;
                }
            }
            SimplifyCFGPass.runOnFunc(function);
        } while (modified);
    }


    private static boolean tryExtractLoop(Loop loop) {
        for (Loop child : loop.children) {
            if (tryExtractLoop(child))
                return true;
        }
        if (loop.parent == null) return false;
        Loop father = loop.parent;
        if (loop.exits.size() != 1 || father.exits.size() != 1) return false;
        // 退出条件复杂
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return false;
        ArrayList<BasicBlock> _pre2 = father.getExit().getPreBlocks();
        if (_pre2.size() > 1 || _pre2.get(0) != father.header) return false;
        // 子循环无副作用
        if (!loop.isNoSideEffect()) return false;
        // 保证子循环不包含父循环的计算结果
        ArrayList<Value> inComing = loop.getInComingValues();
        for (Value v : inComing) {
            if (v instanceof Instruction inst && father.LoopContains(inst.getParentBlock())) {
                return false;
            }
        }
        // 检查memdep关系不改变
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof Instruction.Load load) {
                    if (!MemDepAnalysis.assureNotWritten(block.getParentFunction(), father.header, block, load.getAddr())) {
                        return false;
                    }
                }
            }
        }
        // transform
        // FIXME: 存在不跑的可能性需要加入guard
        LoopCloneInfo info = loop.cloneAndInfo();
        Loop clone = info.cpy;
        // 链接新循环到正确的位置
        BasicBlock fatherPreHeader = father.getPreHeader();
        fatherPreHeader.getTerminator().replaceTarget(father.header, clone.header);

        clone.header.getTerminator().replaceTarget(clone.getExit(), father.header);

        for (Instruction.Phi phi : father.header.getPhiInstructions()) {
            phi.changePreBlock(fatherPreHeader, clone.header);
        }

        for (Instruction.Phi phi : clone.header.getPhiInstructions()) {
            phi.changePreBlock(loop.preHeader, fatherPreHeader);
        }
        // 修改以废除旧循环
        loop.preHeader.getTerminator().replaceTarget(loop.header, loop.getExit());

        for (Instruction.Phi phi : loop.getExit().getPhiInstructions()) {
            phi.changePreBlock(loop.header, loop.preHeader);
            Value val = phi.getOptionalValue(loop.preHeader);
            if (phi.isLCSSA && info.containValue(val)) {
                phi.replaceOptionalValueAtWith(loop.preHeader, info.getReflectedValue(val));
            }
        }
        return true;
    }

    private static void LoopReBuildAndNormalize(Function func) {
        LCSSA.removeOnFunc(func);
        LoopInfo.runOnFunc(func);
        LoopSimplifyForm.runOnFunc(func);
        LCSSA.runOnFunc(func);
    }
}
