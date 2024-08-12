package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Transform.DCE.DeadLoopEliminate;
import midend.Transform.DCE.SimplifyCFGPass;
import midend.Transform.LocalValueNumbering;
import mir.*;
import mir.Module;
import midend.Analysis.result.SCEVinfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoopUnroll {
    private static final int MAXIMUM_LINE = 10;

    // 务必保证 UNROLL_TIMES 为 2 的幂
    private static final int UNROLL_TIMES = 4;

    private static SCEVinfo scevInfo;

    private static int init;
    private static int step;

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
            IndVars.runOnFunc(function);
            scevInfo = AnalysisManager.getSCEV(function);
            for (var loop : function.loopInfo.TopLevelLoops) {
                modified |= tryUnrollLoop(loop);
            }
            DeadLoopEliminate.runOnFunc(function);
            SimplifyCFGPass.runOnFunc(function);
            LocalValueNumbering.runOnFunc(function);
            SimplifyCFGPass.runOnFunc(function);
        } while (modified);
    }

    private static boolean canUnroll(Loop loop) {
        if (loop.tripCount > 0) return false;
        if (loop.exits.size() != 1) return false;
        // 退出条件复杂
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return false;
        Instruction inst = loop.header.getTerminator();
        if (!(inst instanceof Instruction.Branch br)) return false;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return false;
        if (icmp.getCondCode() == Instruction.Icmp.CondCode.EQ || icmp.getCondCode() == Instruction.Icmp.CondCode.NE) {
            return false;
        }
        if (!(icmp.getSrc2() instanceof Constant.ConstantInt) && scevInfo.contains(icmp.getSrc2()))
            icmp.swap();
        if (scevInfo.contains(icmp.getSrc2()))
            return false;
        if (!scevInfo.contains(icmp.getSrc1()))
            return false;
        if (!scevInfo.query(icmp.getSrc1()).isNotNegative())
            return false;
        init = scevInfo.query(icmp.getSrc1()).getInit();
        step = scevInfo.query(icmp.getSrc1()).getStep();
        if (step == 0) return false;
        int loopSize = loop.getSize();
        return loopSize * loop.tripCount <= MAXIMUM_LINE;
    }

    private static boolean tryUnrollLoop(Loop loop) {
        boolean modified = false;
        for (Loop child : loop.getChildrenSnap()) {
            modified |= tryUnrollLoop(child);
        }
        // FIXME : 为了测试，先不考虑包含子循环的情况
        if (!loop.children.isEmpty()) return modified;

        if (!canUnroll(loop)) return modified;
        // 循环不变量的补丁
        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
            if (phi.getOptionalValue(loop.getLatch()) == phi) {
                phi.replaceAllUsesWith(phi.getOptionalValue(loop.getPreHeader()));
                phi.delete();
            }
        }
        // 复制出 UNROLL_TIMES 个
        ArrayList<LoopCloneInfo> infos = new ArrayList<>();
        for (int i = 0; i < UNROLL_TIMES; i++) {
            LoopCloneInfo info = loop.cloneAndInfo();
            infos.add(info);
            if (loop.parent != null) {
                loop.parent.nowLevelBB.addAll(info.cpy.nowLevelBB);
            }
        }

        BasicBlock preHeader = loop.getPreHeader();
        preHeader.getTerminator().replaceTarget(loop.header, infos.get(0).cpy.header);
//        new Instruction.Jump(preHeader, infos.get(0).cpy.header);

        // 处理 remainder
        LoopCloneInfo begin = infos.get(0);
        LoopCloneInfo remainder = loop.cloneAndInfo();
        begin.cpy.header.getTerminator().replaceTarget(loop.getExit(), remainder.cpy.header);
        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
            Instruction.Phi reflectPhi = (Instruction.Phi) remainder.getReflectedValue(phi);
            reflectPhi.removeOptionalValue(preHeader);
            reflectPhi.addOptionalValue(begin.cpy.header, begin.getReflectedValue(phi));
        }

        // 处理begin条件
        Instruction.Terminator beginTerm = begin.cpy.header.getTerminator();
        if (beginTerm instanceof Instruction.Branch beginBr) {
            Value cond = beginBr.getCond();
            if (cond instanceof Instruction.Icmp icmp) {
                Value op1 = icmp.getSrc1();
                Value op2 = icmp.getSrc2();
                // op2 <- (op2 - init) / (UNROLL_TIMES * step) * (UNROLL_TIMES * step) + init - step
                Instruction.Sub sub = new Instruction.Sub(icmp.getParentBlock(), op2.getType(), op2, Constant.ConstantInt.get(init));
                sub.remove();
                icmp.addNext(sub);
                Instruction.Div div = new Instruction.Div(icmp.getParentBlock(), op2.getType(), sub, Constant.ConstantInt.get(UNROLL_TIMES * step));
                div.remove();
                sub.addNext(div);
                Instruction.Mul mul = new Instruction.Mul(icmp.getParentBlock(), op2.getType(), div, Constant.ConstantInt.get(UNROLL_TIMES * step));
                mul.remove();
                div.addNext(mul);
                Instruction.Add add = new Instruction.Add(icmp.getParentBlock(), op2.getType(), mul, Constant.ConstantInt.get(init));
                add.remove();
                mul.addNext(add);
                Instruction.Add add2 = new Instruction.Add(icmp.getParentBlock(), op2.getType(), add, Constant.ConstantInt.get(-step));
                add2.remove();
                add.addNext(add2);
                Instruction.Icmp newIcmp = new Instruction.Icmp(icmp.getParentBlock(), icmp.getCondCode(), op1, add2);
                newIcmp.remove();
                add2.addNext(newIcmp);
                icmp.replaceAllUsesWith(newIcmp);
                icmp.delete();
            } else
                throw new RuntimeException("Unreachable in Unroll Loop");
        } else {
            throw new RuntimeException("Unreachable in Unroll Loop");
        }

        // 处理出口块
        for (var exit : loop.exits) {
            for (Instruction.Phi phi : exit.getPhiInstructions()) {
                LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
                for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                    if (infos.get(0).containValue(entry.getKey())) {
                        if (infos.get(0).containValue(entry.getValue())) {
                            newMap.put(
                                    (BasicBlock) remainder.getReflectedValue(entry.getKey()),
                                    remainder.getReflectedValue(entry.getValue()));
                        } else {
                            newMap.put(
                                    (BasicBlock) remainder.getReflectedValue(entry.getKey()),
                                    entry.getValue());
                        }
                    } else {
                        newMap.put(entry.getKey(), entry.getValue());
                    }
                }
                phi.setOptionalValues(newMap);
            }
        }

        // 处理第一次循环
        LoopCloneInfo end = infos.get(UNROLL_TIMES - 1);
        end.cpy.getLatch().getTerminator().delete();
        new Instruction.Jump(end.cpy.getLatch(), begin.cpy.header);
        for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
            Value val = phi.getOptionalValue(loop.getLatch());
            Instruction.Phi reflectPhi = (Instruction.Phi) begin.getReflectedValue(phi);
            reflectPhi.addOptionalValue(end.cpy.getLatch(), end.getReflectedValue(val));
            reflectPhi.removeOptionalValue(begin.cpy.getLatch());
        }
        // 处理剩余循环
        for (int i = 1; i < UNROLL_TIMES; ++i) {
            LoopCloneInfo now = infos.get(i);
            LoopCloneInfo pre = infos.get(i - 1);
            // 与上一次循环连接
            BasicBlock preLatch = pre.cpy.getLatch();
            preLatch.getTerminator().delete();
            new Instruction.Jump(preLatch, now.cpy.header);

            for (Instruction.Phi phi : loop.header.getPhiInstructions()) {
                Value val = phi.getOptionalValue(loop.getLatch());
                Value reflectPhi = now.getReflectedValue(phi);
                reflectPhi.replaceAllUsesWith(pre.getReflectedValue(val));
                reflectPhi.delete();
            }

            // 优化跳转
            BasicBlock header = now.cpy.header;
            Instruction.Terminator terminator = header.getTerminator();
            if (terminator instanceof Instruction.Branch br) {
                BasicBlock nxt = (br.getThenBlock() == loop.getExit()) ? br.getElseBlock() : br.getThenBlock();
                br.delete();
                new Instruction.Jump(header, nxt);
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
