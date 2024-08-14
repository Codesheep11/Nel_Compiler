package backend.Opt.BackLoop;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;

import java.util.ArrayList;

public class LoopConstLift {
    // 专属于循环不变量外提
    // 一般属于la与li,剩下的其实都是算循环变量了,都已经在外面实现了

    private static int temp_cnt = 0;

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            runOnFunc(function);
        }
    }

    // 需要在函数的尺度新建中转块
    private static void runOnFunc(RiscvFunction function) {
        for (RiscLoop loop : function.loops) {
            while (true) {
                if (!checkOnLoop(function, loop)) break;
            }
        }
        for (RiscLoop loop : function.loops) {
            runOnLoop(loop);
        }
    }

    private static boolean checkOnLoop(RiscvFunction function, RiscLoop riscLoop) {
        for (RiscvBlock block : riscLoop.enterings) {
            if (!(block.riscvInstructions.getLast() instanceof J j)) {
                throw new RuntimeException("wrong type");
            }
            if (!riscLoop.blocks.contains(j.targetBlock)) {
                // 对于所有entering,如果有不是直接j到这里的,那么就不可以直接进行外提
                //检查一下b
                for (RiscvInstruction ri : block.riscvInstructions) {
                    if (ri instanceof B b && riscLoop.blocks.contains(b.targetBlock)) {
                        // 如果满足这个情况,就需要新建一个小的块,然后更换enterings
                        RiscvBlock newBlock = new RiscvBlock(function, "lft_temp_" + temp_cnt++);
                        function.blocks.add(newBlock);
                        riscLoop.enterings.remove(block);
                        riscLoop.enterings.add(newBlock);
                        newBlock.riscvInstructions.addLast(new J(newBlock, J.JType.j, b.targetBlock));
                        newBlock.preBlock.add(block);
                        newBlock.succBlock.add(b.targetBlock);
                        b.targetBlock = newBlock;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void runOnLoop(RiscLoop riscLoop) {
        ArrayList<RiscvInstruction> arrayList = new ArrayList<>();
        for (RiscvBlock block : riscLoop.blocks) {
            for (RiscvInstruction ri : block.riscvInstructions) {
                if (ri instanceof La || ri instanceof Li) {
                    arrayList.add(ri);
                }
            }
        }
        // 是否存在J指向的不是这个循环的情况
        for (RiscvBlock block : riscLoop.enterings) {
            if (!(block.riscvInstructions.getLast() instanceof J j)) {
                throw new RuntimeException("wrong type");
            }
            for (RiscvInstruction ri : arrayList) {
                block.riscvInstructions.insertBefore(ri.myCopy(block), j);
            }
        }
        for (RiscvInstruction ri : arrayList) {
            ri.remove();
        }
        for (RiscLoop subLoop : riscLoop.subLoops) {
            runOnLoop(subLoop);
        }
    }
}
