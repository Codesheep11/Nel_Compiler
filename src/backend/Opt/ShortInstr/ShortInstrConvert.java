package backend.Opt.ShortInstr;

import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import utils.Pair;

import java.util.ArrayList;

public class ShortInstrConvert {
    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                convert(block);
            }
        }
    }

    private static boolean checkRegRange(Reg reg) {
        int range = reg.phyReg.ordinal();
        return range >= 8 && range <= 15;
    }

    private static void convert(RiscvBlock block) {
        ArrayList<Pair<RiscvInstruction, ShortInst>> needReplace = new ArrayList<>();
        for (RiscvInstruction instr : block.riscvInstructions) {
            if (instr instanceof R3 r3) {
                if (r3.type == R3.R3Type.addw || r3.type == R3.R3Type.add) {
                    if (r3.rs1.equals(r3.rd)) {
                        needReplace.add(new Pair<>(instr, new ShortInst.ShortAdd(block, (Reg) r3.rd, (Reg) r3.rs2)));
                    } else if (r3.rs2.equals(r3.rd)) {
                        needReplace.add(new Pair<>(instr, new ShortInst.ShortAdd(block, (Reg) r3.rd, (Reg) r3.rs1)));
                    }
                } else if (r3.type == R3.R3Type.addi) {
                    // 判断移动栈帧
                    if (((Reg) r3.rs1).phyReg == Reg.PhyReg.sp && ((Reg) r3.rd).phyReg == Reg.PhyReg.sp) {
                        long offset = r3.rs2 instanceof Address ad ? -ad.getOffset() : ((Imm) r3.rs2).getVal();
                        if (offset > -512 && offset < 496) {
                            needReplace.add(new Pair<>(instr, new ShortInst.ShortAddi16Sp(block, offset)));
                        }
                    }
                    // 如果两个操作数一样
                    else if (r3.rs1.equals(r3.rd)) {
                        long offset = r3.rs2 instanceof Address ad ? -ad.getOffset() : ((Imm) r3.rs2).getVal();
                        if (offset > -32 && offset < 31) {
                            needReplace.add(new Pair<>(instr, new ShortInst.ShortAddi(block, (Reg) r3.rd, offset)));
                        }
                    }
                }
            } else if (instr instanceof Li li) {
                long imm = li.imm instanceof Address ad ? -ad.getOffset() : ((Imm) li.imm).getVal();
                if (imm >= -32 && imm <= 31) {
                    needReplace.add(new Pair<>(instr, new ShortInst.ShortLi(block, li.reg, imm)));
                }
            } else if (instr instanceof R2 mv && mv.type == R2.R2Type.mv) {
                if (((Reg) mv.rs).phyReg != Reg.PhyReg.zero) {
                    needReplace.add(new Pair<>(instr, new ShortInst.ShortMove(block, (Reg) mv.rd, (Reg) mv.rs)));
                }
            } else if (instr instanceof LS ls) {
                long offset = ls.addr instanceof Address ad ? -ad.getOffset() : ((Imm) ls.addr).getVal();
                if (ls.base.phyReg == Reg.PhyReg.sp) {
                    if (offset > 0 && offset < 255) {
                        if (ls.type == LS.LSType.lw) {
                            needReplace.add(new Pair<>(instr, new ShortInst.LwSp(block, ls.val, offset)));
                        } else if (ls.type == LS.LSType.sw) {
                            needReplace.add(new Pair<>(instr, new ShortInst.SwSp(block, ls.val, offset)));
                        }
                    }
                } else if (checkRegRange(ls.val) && checkRegRange(ls.base)) {
                    if (offset > 0 && offset / 4 < 31) {
                        if (ls.type == LS.LSType.lw) {
                            needReplace.add(new Pair<>(instr, new ShortInst.ShortLw(block, ls.val, ls.base, offset)));
                        } else if (ls.type == LS.LSType.sw) {
                            needReplace.add(new Pair<>(instr, new ShortInst.ShortSw(block, ls.val, ls.base, offset)));
                        }
                    }
                }
            }

        }
        for (Pair<RiscvInstruction, ShortInst> pair : needReplace) {
            block.insertInstBefore(pair.second, pair.first);
            pair.first.remove();
        }
    }
}
