package backend.Opt;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.B;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;
import java.util.HashMap;

public class RegAftCallLoadOpt {
    // 专门用于减少调用函数时寄存器的盲目重复lw
    // 因为只有外部函数才能保证lw出来的必定是外部寄存器

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            LivenessAftBin.runOnFunc(function);
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }

    private static final HashMap<Reg, LS> lastLoad = new HashMap<>();

    private static void runOnBlock(RiscvBlock block) {
        ArrayList<LS> needMove = new ArrayList<>();
        lastLoad.clear();
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof LS ls && (ls.type == LS.LSType.ld
                    || ls.type == LS.LSType.lw || ls.type == LS.LSType.flw)) {
                lastLoad.put(ls.rs1, ls);
            } else if (ri instanceof J j) {
                if (j.type == J.JType.j || j.type == J.JType.ret) break;
                for (Reg reg : lastLoad.keySet()) {
                    int ord = reg.phyReg.ordinal();
                    if ((ord >= 5 && ord <= 7) || (ord >= 28 && ord <= 39) || (ord >= 60 && ord <= 63)) {
                        needMove.add(lastLoad.get(reg));
                    }
                }
                lastLoad.clear();
            } else {
                for (int i = 0; i < ri.getOperandNum(); i++) {
                    if (ri.isUse(i)) {
                        lastLoad.remove(ri.getRegByIdx(i));
                    }
                }
                if (ri instanceof B b) {
                    for (Reg reg : LivenessAftBin.BlockIn.get(b.targetBlock)) {
                        lastLoad.remove(reg);
                    }
                }
            }
        }
        for (LS ls : needMove) {
            ls.remove();
        }
    }
}
