package backend.Opt.BackLoop;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.La;
import backend.riscv.RiscvInstruction.Li;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;

public class LoopConstLift {
    // 专属于循环不变量外提
    // 一般属于la与li,剩下的其实都是算循环变量了,都已经在外面实现了
    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            runOnFunc(function);
        }
    }

    private static void runOnFunc(RiscvFunction function) {
        for (RiscLoop loop : function.loops) {
            runOnLoop(loop);
        }
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
        boolean canRemove = true;
        // 是否存在J指向的不是这个循环的情况
        for (RiscvBlock block : riscLoop.enterings) {
            if (!(block.riscvInstructions.getLast() instanceof J j)) {
                throw new RuntimeException("wrong type");
            }
            if (!riscLoop.blocks.contains(j.targetBlock)) {
                // 对于所有entering,如果有不是直接j到这里的,那么就不可以进行外提
                canRemove = false;
                break;
            }
        }
        if (canRemove) {
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
        }

        for (RiscLoop subLoop : riscLoop.subLoops) {
            runOnLoop(subLoop);
        }
    }
}
