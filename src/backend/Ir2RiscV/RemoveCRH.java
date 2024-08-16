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

    private static void runOnFunc(RiscvFunction function) {
        for (int i = 0; i < function.blocks.size(); i++) {
            RiscvBlock block = function.blocks.get(i);
            Iterator<RiscvInstruction> iterator = block.riscvInstructions.iterator();
            while (iterator.hasNext()) {
                RiscvInstruction r = iterator.next();
                if (r instanceof ConstRemHelper c) {
                    RiscvBlock newBlock1 = new RiscvBlock(function, c.name1);
                    // 放addiw
                    RiscvBlock newBlock2 = new RiscvBlock(function, c.name2);
                    // 放后面的
                    function.blocks.add(i + 1, newBlock1);
                    function.blocks.add(i + 2, newBlock2);
                    // 将本块的后面指令(包括crh)删除并拷贝到这个新的块
                    iterator.remove();
                    ArrayList<RiscvInstruction> toMove = new ArrayList<>();
                    while (iterator.hasNext()) {
                        RiscvInstruction others = iterator.next();
                        toMove.add(others);
                    }
                    for (RiscvInstruction instruction : toMove) {
                        instruction.remove();
                        newBlock2.addInstLast(instruction);
                    }
                    block.addInstLast(new R2(block, c.reg, c.src, R2.R2Type.mv));
                    newBlock2.succBlock.addAll(block.succBlock);
                    block.succBlock.clear();
                    for (RiscvBlock other : newBlock2.succBlock) {
                        other.preBlock.remove(block);
                        other.preBlock.add(newBlock2);
                    }
                    block.addInstLast(new B(block, B.BType.bge, c.src,
                            Reg.getPreColoredReg(Reg.PhyReg.zero, 32), newBlock2, 1.0));
                    block.addInstLast(new J(block, J.JType.j, newBlock1));
                    newBlock1.addInstLast(new R3(newBlock1, c.reg, c.src, new Imm(1), R3.R3Type.addiw));
                    newBlock1.addInstLast(new J(newBlock1, J.JType.j, newBlock2));
                    break;
                }
            }
        }
    }
}
