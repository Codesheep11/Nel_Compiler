package backend.Ir2RiscV;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;

import java.util.ArrayList;
import java.util.Iterator;

public class RemoveCRH {
    // 删除所有ConstRemHelper
    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            runOnFunc(function);
        }
    }
    // crh->
    // bge -> 继续的
    // j ->额外的,放addiw的

    private static int cnt = 0;

    private static String head = "rem_helper_BB";

    private static void runOnFunc(RiscvFunction function) {
        for (int i = 0; i < function.blocks.size(); i++) {
            RiscvBlock block = function.blocks.get(i);
            Iterator<RiscvInstruction> iterator = block.riscvInstructions.iterator();
            while (iterator.hasNext()) {
                RiscvInstruction r = iterator.next();
                if (r instanceof ConstRemHelper c) {
                    RiscvBlock newBlock = new RiscvBlock(function, head + cnt++);
                    // 放addiw
                    // 放后面的
                    function.blocks.add(i + 1, newBlock);
                    // 将本块的后面指令(包括crh)删除并拷贝到这个新的块
                    iterator.remove();
                    ArrayList<RiscvInstruction> toMove = new ArrayList<>();
                    while (iterator.hasNext()) {
                        RiscvInstruction others = iterator.next();
                        toMove.add(others);
                    }
                    for (RiscvInstruction instruction : toMove) {
                        instruction.remove();
                        newBlock.addInstLast(instruction);
                    }
                    block.addInstLast(new R2(block, c.reg, c.src, R2.R2Type.mv));
                    // 由于是在所有执行之后使用,因此完全不用考虑维护得东西
                    block.addInstLast(new B(block, B.BType.bge, c.src,
                            Reg.getPreColoredReg(Reg.PhyReg.zero, 32), newBlock, 1.0));
                    int x = c.imm - 1;
                    if (x >= 2047) {
                        block.addInstLast(new Li(block, c.reg, new Imm(x)));
                        block.addInstLast(new R3(block, c.reg, c.src, c.reg, R3.R3Type.addw));
                    } else {
                        block.addInstLast(new R3(block, c.reg, c.src, new Imm(x), R3.R3Type.addiw));
                    }
                    break;
                }
            }
        }
    }
}
