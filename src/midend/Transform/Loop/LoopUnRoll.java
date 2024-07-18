package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Transform.Loop.LoopCloneInfo;
import mir.*;
import mir.Module;
import mir.result.SCEVinfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;


public class LoopUnRoll {
    private static final int MAXIMUM_LINE = 50000;

    private static SCEVinfo scevInfo;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
                runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        AnalysisManager.refreshCFG(function);
        AnalysisManager.refreshSCEV(function);
        scevInfo = AnalysisManager.getSCEV(function);
        for (var loop : function.loopInfo.TopLevelLoops) {
            tryUnrollLoop(loop);
        }
    }

    // 确保是子循环
    public static boolean tryUnrollLoop(Loop loop) {

        // TODO: 或许可以优化展开后循环内的跳转
        // TODO: 改进不止展开一级循环
        if (!loop.children.isEmpty()) return false;
        if (loop.tripCount <= 0) return false;
        if (loop.exits.size() > 1) return false;
        if (loop.getExit().getPreBlocks().size() > 1) return false;
        int loopSize = loop.getSize();
        if (loopSize * loop.tripCount > MAXIMUM_LINE) return false;
        // 展开为 loop.tripCount + 1 次
        ArrayList<LoopCloneInfo> infos = new ArrayList<>();
        for (int i = 0; i <= loop.tripCount; i++) {
            LoopCloneInfo info = loop.cloneAndInfo();
            infos.add(info);
        }

        BasicBlock preHeader = loop.getPreHeader();
        preHeader.getTerminator().delete();
        new Instruction.Jump(preHeader, infos.get(0).cpy.header);

        // 处理第一次循环
        // FIXME: 简化
        BasicBlock begin = infos.get(0).cpy.header;
        for (Instruction inst : begin.getInstructionsSnap()) {
            if (inst instanceof Instruction.Phi phi) {
                Value val = phi.getOptionalValue(loop.getPreHeader());
                phi.replaceAllUsesWith(val);
                phi.delete();
            }
        }
        // 处理剩余循环
        for (int i = 1; i <= loop.tripCount; i++) {
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
        }
//        Loop end = infos.get(loop.tripCount).cpy;
        // 处理出口块
        for (var exit : loop.exits) {
            for (Instruction.Phi phi : exit.getPhiInstructions()) {
                LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
                for (Map.Entry<BasicBlock, Value> entry : phi.getOptionalValues().entrySet()) {
                    if (infos.get(0).containValue(entry.getKey())) {
                        if (infos.get(0).containValue(entry.getValue())) {
                            newMap.put(
                                    (BasicBlock) infos.get(loop.tripCount).getReflectedValue(entry.getKey()),
                                    infos.get(loop.tripCount).getReflectedValue(entry.getValue()));
                        } else {
                            newMap.put(
                                    (BasicBlock) infos.get(loop.tripCount).getReflectedValue(entry.getKey()),
                                    entry.getValue());
                        }
                    } else {
                        newMap.put(entry.getKey(), entry.getValue());
                    }
                }
                phi.setOptionalValues(newMap);
            }
        }
        return true;
    }


}
