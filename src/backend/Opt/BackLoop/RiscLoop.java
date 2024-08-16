package backend.Opt.BackLoop;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import midend.Transform.Loop.LoopInfo;
import mir.BasicBlock;
import mir.Function;
import mir.Loop;

import java.util.HashMap;
import java.util.HashSet;

public class RiscLoop {
    // 有若干个header，不在循环节中
    // 一个函数的
    public final HashSet<RiscvBlock> blocks = new HashSet<>();
    public final HashSet<RiscvBlock> enterings = new HashSet<>();


    public final HashSet<RiscLoop> subLoops = new HashSet<>();
    // 都提到外面可能开的内容有些太多了

    public static void buildLoops(RiscvFunction rf, Function mf, HashMap<BasicBlock, RiscvBlock> map) {
        LoopInfo info = mf.loopInfo;
        if (info == null) return;
        for (Loop loop : info.TopLevelLoops) {
            rf.loops.add(recBuildLoop(loop, map));
        }
    }

    private static RiscLoop recBuildLoop(Loop loop, HashMap<BasicBlock, RiscvBlock> map) {
        RiscLoop riscLoop = new RiscLoop();
        for (BasicBlock bb : loop.enterings) {
            riscLoop.enterings.add(map.get(bb));

        }
        for (BasicBlock block : loop.nowLevelBB) {
            // 考虑到提出的量都需要同时存在,因此就不考虑顺序了
            riscLoop.blocks.add(map.get(block));
        }
        for (Loop subLoop : loop.children) {
            riscLoop.subLoops.add(recBuildLoop(subLoop, map));
        }
        return riscLoop;
    }
}
