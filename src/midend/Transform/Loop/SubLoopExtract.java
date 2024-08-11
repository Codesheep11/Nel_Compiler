package midend.Transform.Loop;

import mir.Module;
import mir.*;

import java.util.ArrayList;
import java.util.Stack;


public class SubLoopExtract {

    private static boolean modified = false;

    private static ArrayList<Loop> workList = new ArrayList<>();

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunction(function);
        }
    }

    public static void runOnFunction(Function function) {
        //dfs后序遍历得到所有的循环
        while (true) {
            modified = false;
            LoopInfo.runOnFunc(function);
            LoopSimplifyForm.runOnFunc(function);
            LoopInfo.runOnFunc(function);
            CollectWorkList(function);
            for (Loop loop : workList) {
                runOnLoop(loop);
                if (modified) break;
            }
            if (!modified) break;
        }
    }

    private static void CollectWorkList(Function function) {
        workList.clear();
        //dfs后序遍历得到所有的循环
        for (Loop loop : function.loopInfo.TopLevelLoops) {
            dfs(loop);
        }
    }

    private static void dfs(Loop loop) {
        for (Loop child : loop.children) {
            dfs(child);
        }
        workList.add(loop);
    }

    /**
     * 检查当前循环的child是否能够提升
     *
     * @param loop
     * @return
     */
    public static void runOnLoop(Loop loop) {
        if (loop.children.isEmpty()) return;
        for (Loop child : loop.children) {
            if (child.exits.size() != 1) continue;
            boolean canLift = true;
            if (child.isNoSideEffect()) {
                ArrayList<Value> inComing = child.getInComingValues();
                for (Value v : inComing) {
                    if (v instanceof Instruction inst && loop.LoopContains(inst.getParentBlock())) {
                        canLift = false;
                        break;
                    }
                }
            }
            if (canLift) {
                modified = true;
                //todo: 将子循环提升到当前循环，无视循环结构
                break;
            }
        }
    }
}
