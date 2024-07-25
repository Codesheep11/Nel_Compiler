package midend.Transform.Loop;

import midend.Analysis.Manager.ModuleAnalysisManager;
import mir.BasicBlock;
import mir.Function;
import mir.Loop;
import mir.Module;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopInfo {
    private Function function;
    public ArrayList<Loop> TopLevelLoops = new ArrayList<>();

    public LoopInfo(Function function) {
        this.function = function;
        runLoopAnalysis();
    }

    public static void build(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            function.loopInfo = new LoopInfo(function);
        }
    }


    public void runLoopAnalysis() {
        clearBlocksLoopInfo();
//        function.buildControlFlowGraph();
        ModuleAnalysisManager.refreshCFG(function);
        function.buildDominanceGraph();

        LoopInfo4Func();
        for (Loop loop : TopLevelLoops) {
            genEnterExit4Loop(loop);
            LoopSimplifyForm.run(loop);
//            LCSSA.run(loop);
        }
        function.buildControlFlowGraph();
//        printLoopInfo();
    }

    private void clearBlocksLoopInfo() {
        for (BasicBlock bb : function.getBlocks()) {
            bb.loop = null;
        }
    }

    private void genEnterExit4Loop(Loop loop) {
        for (Loop child : loop.children) {
            genEnterExit4Loop(child);
        }
        //生成loop的entering exiting exit
        HashSet<BasicBlock> allBB = loop.getAllBlocks();
        for (BasicBlock bb : loop.header.getPreBlocks()) {
            if (!allBB.contains(bb)) {
                loop.enterings.add(bb);
            }
        }
        for (BasicBlock bb : loop.nowLevelBB) {
            for (BasicBlock succ : bb.getSucBlocks()) {
                if (!allBB.contains(succ)) {
                    loop.exitings.add(bb);
                    loop.exits.add(succ);
                }
            }
        }
    }

    private void printLoopInfo() {
        System.out.println("LoopInfo:Function: " + function.getName());
        for (Loop loop : TopLevelLoops) {
            loop.LoopInfoPrint();
            System.out.println();
        }
    }

    private void LoopInfo4Func() {
        //支配树后续遍历，保证子循环在父循环之前被发现
        ArrayList<BasicBlock> postOrderTravel = function.getDomTreePostOrder();
        ArrayList<BasicBlock> backEdges = new ArrayList<>();
        for (BasicBlock header : postOrderTravel) {
            backEdges.clear();
            for (BasicBlock pre : header.getPreBlocks()) {
                if (ModuleAnalysisManager.dominate(header, pre)) {
//                    System.out.println("backEdge: " + pre.getLabel() + " -> " + header.getLabel());
                    backEdges.add(pre);
                }
            }
            if (!backEdges.isEmpty()) {
//                System.out.println("header: " + header.getLabel());
                Loop loop = new Loop(header);
                TopLevelLoops.add(loop);
                discoverAndMapSubloop(loop, backEdges);
            }
        }
    }

    private void discoverAndMapSubloop(Loop loop, ArrayList<BasicBlock> backEdges) {
        ArrayList<BasicBlock> reverseCFGWorkList = new ArrayList<>(backEdges);
        loop.latchs.addAll(backEdges);
        HashSet<BasicBlock> visited = new HashSet<>();
        while (!reverseCFGWorkList.isEmpty()) {
            BasicBlock predBB = reverseCFGWorkList.remove(reverseCFGWorkList.size() - 1);
            if (visited.contains(predBB)) continue;
            visited.add(predBB);
//            System.out.println("predBB: " + predBB.getLabel());
            Loop subLoop = predBB.loop;
            if (subLoop == null) {
                loop.addNowLevelBB(predBB);
                if (predBB.equals(loop.header)) continue;
                reverseCFGWorkList.addAll(predBB.getPreBlocks());
            }
            else {
                while (subLoop.parent != null) {
                    subLoop = subLoop.parent;
                }
                if (subLoop == loop) continue;
                loop.addChildLoop(subLoop);
                TopLevelLoops.remove(subLoop);
                predBB = subLoop.header;
                reverseCFGWorkList.addAll(predBB.getPreBlocks());
            }
        }
    }

}
