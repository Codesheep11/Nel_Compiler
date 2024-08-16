package backend.Ir2RiscV;

import backend.StackManager;
import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import manager.Manager;

import java.util.ArrayList;
import java.util.HashSet;

public class AfterRA {
    public static void run(RiscvModule riscvModule) {
        Manager.afterRegAssign = true;
        spMove(riscvModule);
        LS2LiAddLS(riscvModule);
        Addi2LiAdd(riscvModule);
        uselessAddiRemove(riscvModule);
    }

    public static void spMove(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            int size = StackManager.getInstance().getFuncSize(function.name);
            function.getEntry().addInstFirst(new R3(function.getEntry(),
                    Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                    Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                    new Address(size, function.name),
                    R3.R3Type.addi));
            for (RiscvBlock rb : function.exits) {
                R3 r3 = new R3(rb,
                        Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                        Reg.getPreColoredReg(Reg.PhyReg.sp, 64),
                        new Address(-size, function.name),
                        R3.R3Type.addi);
                rb.insertInstBefore(r3, rb.riscvInstructions.getLast());
            }
        }
    }


    public static void LS2LiAddLS(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock riscvBlock : function.blocks) {
                ArrayList<LS> needInsert = new ArrayList<>();
                for (RiscvInstruction ri : riscvBlock.riscvInstructions) {
                    if (ri instanceof LS) {
                        needInsert.add((LS) ri);
                    }
                }
                needInsert.forEach(LS::replaceMe);
            }
        }
    }

    public static void Addi2LiAdd(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock riscvBlock : function.blocks) {
                ArrayList<R3> needInsert = new ArrayList<>();
                for (RiscvInstruction ri : riscvBlock.riscvInstructions) {
                    if (ri instanceof R3) {
                        needInsert.add((R3) ri);
                    }
                }
                needInsert.forEach(R3::replaceMe);
            }
        }
    }

    public static void uselessAddiRemove(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                HashSet<RiscvInstruction> needRemove = new HashSet<>();
                for (RiscvInstruction instr : block.riscvInstructions) {
                    if (instr instanceof R3 r3) {
                        if (r3.type == R3.R3Type.addw || r3.type == R3.R3Type.add) {
                            if (r3.rd.equals(r3.rs1) && r3.rs2.equals(Reg.getPreColoredReg(Reg.PhyReg.zero, 32))) {
                                needRemove.add(r3);
                            } else if (r3.rd.equals(r3.rs2) && r3.rs1.equals(Reg.getPreColoredReg(Reg.PhyReg.zero, 32))) {
                                needRemove.add(r3);
                            }
                        } else if (r3.type == R3.R3Type.addi) {
                            if (r3.rs2 instanceof Address && r3.rs1.equals(r3.rd) && ((Address) r3.rs2).getOffset() == 0) {
                                needRemove.add(r3);
                            } else if (r3.rs2 instanceof Imm && r3.rs1.equals(r3.rd) && ((Imm) r3.rs2).getVal() == 0) {
                                needRemove.add(r3);
                            }
                        }
                    }
                }
                for (RiscvInstruction ri : needRemove) {
                    ri.remove();
                }
            }
        }
    }

}
