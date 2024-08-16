package midend.Transform.Loop;

import midend.Analysis.AnalysisManager;
import midend.Analysis.result.SCEVinfo;
import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 1. 对于子循环出口的LCSSA，如果在子循环中全为计算操作，并在父循环中store，将其直接在子循环中store
 * 2. 对于子循环中的load + 计算 + store，而该地址是循环不变量，则设置一个temp，并将store操作移出循环
 */
public class LoopNestTemp {
    private static SCEVinfo scevInfo;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }


    public static void runOnFunc(Function function) {
        scevInfo = AnalysisManager.getSCEV(function);
        for (Loop loop : function.loopInfo.TopLevelLoops) {
//            runOnLoop(loop);
        }
    }

    private static void runOnLoop(Loop loop) {
        for (Loop child : loop.children) {
            runOnLoop(child);
        }
        if (loop.children.isEmpty()) return;
        if (loop.exits.size() != 1) return;
        ArrayList<BasicBlock> _pre = loop.getExit().getPreBlocks();
        if (_pre.size() > 1 || _pre.get(0) != loop.header) return;
        Instruction terminator = loop.header.getTerminator();
        if (!(terminator instanceof Instruction.Branch br)) return;
        Value cond = br.getCond();
        if (!(cond instanceof Instruction.Icmp icmp)) return;
    }

    public static void runTemp2Mem4Loop(Loop loop) {
        for (Loop child : loop.children) {
            runTemp2Mem4Loop(child);
        }
    }

    public static void runMem2Temp4Loop(Loop loop) {
        for (Loop child : loop.children) {
            runMem2Temp4Loop(child);
        }
        if (loop.exits.size() != 1) return;
        HashMap<Value, Integer> loadStoreMap = new HashMap<>();
        boolean hasStore = false;
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instruction inst : block.getMainInstructions()) {
                if (inst instanceof Instruction.Load load) {
                    Value ptr = load.getAddr();
                    loadStoreMap.put(ptr, loadStoreMap.getOrDefault(ptr, 0) | 1);
                }
                else if (inst instanceof Instruction.Store store) {
                    hasStore = true;
                    Value ptr = store.getAddr();
                    loadStoreMap.put(ptr, loadStoreMap.getOrDefault(ptr, 0) | 2);
                }
            }
        }
        if (!hasStore) return;
        for (Value ptr : loadStoreMap.keySet()) {
            if (loadStoreMap.get(ptr) == 3) {

            }
        }
    }
}
