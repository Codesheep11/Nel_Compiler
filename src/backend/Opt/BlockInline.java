package backend.Opt;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class BlockInline {
    // 执行完这个之后，指令格式的状态应该是,中间可能有B，但是最后一定是一个J

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            boolean con = true;
            while (con) {
                con = preSimplify(function);
            }
            con = true;
            while (con) {
                con = blockInline(function);
            }
        }
    }

    private static final int MAX_LEN = 50;

    public static boolean preSimplify(RiscvFunction func) {
        boolean modify = false;
        modify |= SimplifyCFG.redirectGoto(func);
        modify |= removeUnusedLabels(func);
        return modify;
    }

    public static boolean removeUnusedLabels(RiscvFunction func) {
        ArrayList<RiscvBlock> blocks = func.blocks;
        HashSet<RiscvBlock> visited = new HashSet<>();
        Queue<RiscvBlock> q = new LinkedList<>();
        q.add(blocks.get(0));
        while (q.size() != 0) {
            RiscvBlock now = q.poll();
            visited.add(now);
            for (RiscvInstruction ri : now.riscvInstructions) {
                if (ri instanceof B) {
                    RiscvBlock tar = ((B) ri).targetBlock;
                    if (!visited.contains(tar)) {
                        q.add(tar);
                        visited.add(tar);
                    }
                } else if (ri instanceof J && ((J) ri).type == J.JType.j) {
                    RiscvBlock tar = ((J) ri).targetBlock;
                    if (!visited.contains(tar)) {
                        q.add(tar);
                        visited.add(tar);
                    }
                }
            }
        }
        return blocks.removeIf(block -> !visited.contains(block));
    }

    public static boolean blockInline(RiscvFunction function) {
        // 只需要一个一个复制即可，删除可以交给广搜解决
        boolean modify = false;
        for (RiscvBlock block : function.blocks) {
            // 如果最后一个是j指令并且长度比阈值小，那么就直接复制过来
            // 问题,如果仅仅是一个短小的块,是否需要复制一堆到后面来?
            J j = (J) block.riscvInstructions.getLast();
            if (j.type == J.JType.j) {
                RiscvBlock to = j.targetBlock;
                if (to == block) continue;
                // 判断是否这个块是个自指块，是的话就不能复制
                if (to.riscvInstructions.getLast() instanceof J j1) {
                    if (j1.type == J.JType.j) {
                        if (j1.targetBlock == to) {
                            continue;
                        }
                    }
                }
                if (to.riscvInstructions.size() <= MAX_LEN) {
                    block.riscvInstructions.removeLast();
                    for (RiscvInstruction ri : to.riscvInstructions) {
                        block.riscvInstructions.addLast(ri.myCopy(block));
                    }
                    modify = true;
                }
            }
        }
        return removeUnusedLabels(function) || modify;
    }
}
