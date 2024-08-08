package backend.Opt.MemoryOpt;

import backend.Opt.Liveness.LivenessAftBin;
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

public class RegAftExternCallLoadOpt {
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
                for (int i = 0; i < ri.getOperandNum(); i++) {
                    if (ri.isUse(i)) {
                        lastLoad.remove(ri.getRegByIdx(i));
                    }
                }
                lastLoad.put(ls.val, ls);
            } else if (ri instanceof J j) {
                if (j.type == J.JType.j || j.type == J.JType.ret) break;
                // 直接对所有call搞删除?可能是后面没用上
                // 考虑到寄存器分配后的活跃变量分析范围较宽
                // 还是直接对所有external搞吧
                if (j.isExternel()) {
                    for (Reg reg : lastLoad.keySet()) {
                        int ord = reg.phyReg.ordinal();
                        if ((ord >= 5 && ord <= 7) || (ord >= 28 && ord <= 39) || (ord >= 60 && ord <= 63)) {
                            needMove.add(lastLoad.get(reg));
                        }
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
