package backend.Opt.GPpooling;

import backend.operand.Imm;
import backend.riscv.*;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.La;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import utils.Pair;

import java.util.ArrayList;
import java.util.Iterator;

public class GlobalFloat2roPool {
    // 全局非数组变量当作常量池,然后用gp来存取,省的每次都专门la一下地址很麻烦
    // 在寄存器分配前搞
    private static final GPpool gPpool = new GPpool();

    public static void run(RiscvModule riscvModule) {

        Iterator<RiscvGlobalVar> iterator = riscvModule.globList.iterator();
        while (iterator.hasNext()) {
            RiscvGlobalVar rb = iterator.next();
            if (rb instanceof RiscvFloat) {
                gPpool.add(rb);
                iterator.remove();
            }
        }
        gPpool.init();
        // 对于所有使用这里面值的指令,将la+lw转换成lw偏移
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }

    private static void runOnBlock(RiscvBlock block) {
        for (int i = 0; i < block.riscvInstructions.size() - 1; i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            RiscvInstruction next = block.riscvInstructions.get(i + 1);
            if (now instanceof La la && next instanceof LS ls) {
                if (gPpool.queryOffset(la.content) != -1) {
                    int off = gPpool.queryOffset(la.content);
                    if (la.reg.equals(ls.rs2)) {
                        // 代表可以换掉
                        la.content = gPpool;
                        ls.addr = new Imm(off);
                    }
                }
            }
        }
    }
}