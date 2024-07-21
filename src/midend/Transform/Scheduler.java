package midend.Transform;

import midend.Analysis.AnalysisManager;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Scheduler {
    //维护每个Block出口活跃的指令
    private static HashMap<BasicBlock, HashSet<Instruction>> outLiveMap = new HashMap<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    private static void runOnFunc(Function func) {
        outLiveMap.clear();
        for (BasicBlock block : func.getBlocks()) outLiveMap.put(block, new HashSet<>());
        for (BasicBlock block : func.getBlocks()) {
            for (Instruction.Phi phi : block.getPhiInstructions()) {
                for (BasicBlock pre : block.getPreBlocks()) {
                    if (phi.getOptionalValue(pre) instanceof Instruction out)
                        outLiveMap.get(pre).add(out);
                }
            }
        }
        dfsScheduleOnBlock(func.getEntry());
    }

    //dfs
    private static void dfsScheduleOnBlock(BasicBlock block) {
        for (Value operand : block.getTerminator().getOperands()) {
            if (operand instanceof Instruction instr) {
                outLiveMap.get(block).add(instr);
            }
        }
        for (BasicBlock child : AnalysisManager.getDomTreeChildren(block)) {
            dfsScheduleOnBlock(child);
        }
        //开始重排

    }
}
