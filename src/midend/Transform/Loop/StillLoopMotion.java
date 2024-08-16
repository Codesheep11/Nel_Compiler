package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.SimplifyCFGPass;
import mir.*;
import mir.Module;

import java.util.ArrayList;

/**
 * 将 "静止" 的循环移动到外层循环
 *
 */

public class StillLoopMotion {

    // FIXME: 还需要进一步细化
    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {

        boolean modified;
        do {
            modified = false;
            AnalysisManager.refreshCFG(function);
            LoopReBuildAndNormalize(function);
            for (Loop loop : function.loopInfo.TopLevelLoops) {
                modified |= tryMoveSubLoop(loop);
            }
            SimplifyCFGPass.runOnFunc(function);
        } while (modified);
    }

    private static boolean tryMoveSubLoop(Loop loop) {
        for (Loop subLoop : loop.children) {
            if(tryMoveSubLoop(subLoop))
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

        Instruction inst = loop.header.getTerminator();
        if (!(inst instanceof Instruction.Branch br)) return false;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return false;
        if (icmp.getSrc2() instanceof Instruction.Phi)
            icmp.swap();
        if (!(icmp.getSrc1() instanceof Instruction.Phi indvar)) return false;
        Value bound = icmp.getSrc2();
        if (bound instanceof Instruction boundVal) {
            if (father.defValue(boundVal)) {
                return false;
            }
        }
        Value init = indvar.getOptionalValue(loop.getPreHeader());
        if (!(init instanceof Instruction.Phi initPhi)) return false;
        if (initPhi.getParentBlock() != father.header) return false;
        Value other = initPhi.getOptionalValue(father.getLatch());
        if (!(other instanceof Instruction.Phi lc)) return false;
        // NOTE: 这里的判定条件还可以进一步细化
        if (lc.isLCSSA) {
            other = lc.getOptionalValue(loop.header);
        }
        if (other != indvar) return false;
        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
            Value initVal = phi.getOptionalValue(loop.getPreHeader());
            if (initVal instanceof Instruction) {
                if (initVal instanceof Instruction.Phi _phi) {
                    if (_phi.getParentBlock() != father.header)
                        return false;
                } else {
                    return false;
                }
            }
        }
        // 经过以上条件判定，可知该循环在父亲内部至多只跑一次

        // LoopPeeling 1 层
        LoopCloneInfo fatherInfo = father.cloneAndInfo();
        Loop fatherClone = fatherInfo.cpy;

        BasicBlock fatherPreHeader = father.getPreHeader();
        fatherPreHeader.getTerminator().replaceTarget(father.header, fatherClone.header);

        fatherClone.getLatch().getTerminator().replaceTarget(fatherClone.header, father.header);

        for (Instruction.Phi phi : father.header.getPhiInstructions()) {
            Value incVal = phi.getOptionalValue(father.getLatch());
            if (fatherInfo.containValue(incVal)) {
                phi.removeOptionalValue(fatherPreHeader);
                phi.addOptionalValue(fatherClone.getLatch(), fatherInfo.getReflectedValue(incVal));
            } else
                phi.changePreBlock(fatherPreHeader, fatherClone.getLatch());
        }

        for (Instruction.Phi phi : fatherClone.header.getPhiInstructions()) {
            phi.removeOptionalValue(fatherClone.getLatch());
        }

        for (Instruction.Phi phi : father.getExit().getPhiInstructions()) {
            Value val = phi.getOptionalValue(father.header);
            if (fatherInfo.containValue(val)) {
                phi.addOptionalValue(fatherClone.header, fatherInfo.getReflectedValue(val));
            } else
                phi.addOptionalValue(fatherClone.header, val);
        }

        loop.header.getTerminator().delete();
        new Instruction.Jump(loop.header, loop.getExit());

        // FIXME: 存在不跑的可能性需要加入guard
        // 子循环内部使用的外部值需要替换 但是比较困难
//        LoopCloneInfo info = loop.cloneAndInfo();
//        Loop clone = info.cpy;
//        BasicBlock fatherPreHeader = father.getPreHeader();
//        fatherPreHeader.getTerminator().replaceTarget(father.header, clone.header);
//
//        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
//            Value initVal = phi.getOptionalValue(loop.getPreHeader());
//            if (initVal instanceof Instruction.Phi faPhi) {
//                Instruction.Phi reflectPhi = (Instruction.Phi) info.getReflectedValue(phi);
//                reflectPhi.replaceOptionalValueAtWith(loop.getPreHeader(), faPhi.getOptionalValue(fatherPreHeader));
//            }
//        }
//
//        for (Instruction.Phi phi : clone.header.getPhiInstructions()) {
//            phi.changePreBlock(loop.getPreHeader(), fatherPreHeader);
//        }
//
//        Instruction.Branch cpyBr = (Instruction.Branch) clone.header.getTerminator();
//        cpyBr.replaceUseOfWith(loop.getExit(), father.header);
//
//        for (Instruction.Phi phi : father.header.getPhiInstructions()) {
//            phi.changePreBlock(fatherPreHeader, clone.header);
//        }
//
//        loop.header.getTerminator().delete();
//        new Instruction.Jump(loop.header, loop.getExit());
        return true;
    }

    private static void LoopReBuildAndNormalize(Function func) {
        LCSSA.removeOnFunc(func);
        LoopInfo.runOnFunc(func);
        LoopSimplifyForm.runOnFunc(func);
        LCSSA.runOnFunc(func);
    }
}
