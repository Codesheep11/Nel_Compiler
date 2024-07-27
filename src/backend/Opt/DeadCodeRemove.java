package backend.Opt;

import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.HashMap;
import java.util.HashSet;

public class DeadCodeRemove {
    // 对于一个def,如果它的值在下一次def前还没用到,那么就直接删除
    private static final HashMap<Reg, RiscvInstruction> lastDefandUnuse = new HashMap<>();

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }

    private static void runOnBlock(RiscvBlock block) {
        lastDefandUnuse.clear();
        HashSet<RiscvInstruction> needRemove = new HashSet<>();
        for (RiscvInstruction ri : block.riscvInstructions) {
            // 如果是call的话，应该将所有没使用的定义，不是X10-X16之间的全部删除
            if (ri instanceof J j && j.type == J.JType.call) {
                for (Reg reg : lastDefandUnuse.keySet()) {
                    int ord = reg.phyReg.ordinal();
                    if ((ord >= 7 && ord <= 9) || (ord >= 28 && ord <= 31)) {
                        // 如果是t这种暂时寄存器
                        needRemove.add(lastDefandUnuse.get(reg));
                    }
                }
            } else {
                for (int i = ri.getOperandNum() - 1; i >= 0; i--) {
                    // 由于def都在第一个,所以应当倒序查找
                    if (ri.isUse(i)) {
                        lastDefandUnuse.remove(ri.getRegByIdx(i));
                    } else if (ri.isDef(i)) {
                        if (lastDefandUnuse.containsKey(ri.getRegByIdx(i))) {
                            needRemove.add(lastDefandUnuse.get(ri.getRegByIdx(i)));
                        }
                        lastDefandUnuse.put(ri.getRegByIdx(i), ri);
                    }
                }
            }
        }
        for (RiscvInstruction riscvInstruction : needRemove) {
            riscvInstruction.remove();
        }
    }
}
