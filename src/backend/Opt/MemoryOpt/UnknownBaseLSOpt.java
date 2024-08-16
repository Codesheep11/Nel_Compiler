package backend.Opt.MemoryOpt;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.R2;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;
import utils.Pair;

import java.util.ArrayList;

public class UnknownBaseLSOpt {
    // 为什么要拆开呢?因为要避免耦合
    // 在memopt之后优化
    public static class UBRecord {
        private final Reg reg;
        private final long offset;

        private final Reg base;

        public UBRecord(Reg reg, long off, Reg base) {
            this.reg = reg;
            this.offset = off;
            this.base = base;
        }

        public Reg getReg() {
            return reg;
        }

        public long getOffset() {
            return offset;
        }

        public Reg getBase() {
            return base;
        }

        public UBRecord myCopy(Reg newReg) {
            return new UBRecord(newReg, this.offset, base);
        }
    }

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }

    public static final ArrayList<UBRecord> records = new ArrayList<>();

    public static void removeByBase(Reg base) {
        records.removeIf(ubRecord -> ubRecord.base.equals(base));
    }

    public static void removeByReg(Reg reg) {
        records.removeIf(ubRecord -> ubRecord.reg.equals(reg));
    }

    private static void removeByReWrite(UBRecord cord) {
        records.removeIf(ubRecord -> ubRecord.base.equals(cord.base) && ubRecord.offset == cord.offset);
    }

    public static ArrayList<UBRecord> queryByOff(Reg bestReg, Reg base, long offset) {
        ArrayList<UBRecord> ans = new ArrayList<>();
        for (UBRecord br : records) {
            if (br.offset == offset && br.getBase().equals(base)) {
                if (br.reg.equals(bestReg)) {
                    ans.add(0, br);
                } else {
                    ans.add(br);
                }
            }
        }
        return ans;
    }

    // 这个仅仅为了避免出现外提后找不到全局指针的情况
    private static void runOnBlock(RiscvBlock block) {
        ArrayList<Pair<LS, R2>> ls2move = new ArrayList<>();
        for (RiscvInstruction instr : block.riscvInstructions) {
            if (instr instanceof LS ls && ls.base.phyReg != Reg.PhyReg.sp) {
                long off = ((Imm) ls.addr).getVal();
                if (ls.type == LS.LSType.ld || ls.type == LS.LSType.lw || ls.type == LS.LSType.flw) {
                    ArrayList<UBRecord> list = queryByOff(ls.val, ls.base, off);
                    removeByReg(ls.val);
                    removeByBase(ls.val);
                    if (!list.isEmpty()) {
                        R2.R2Type r2Type = ls.val.regType == Reg.RegType.GPR ? R2.R2Type.mv : R2.R2Type.fmv;
                        ls2move.add(new Pair<>(ls, new R2(block, ls.val, list.get(0).reg, r2Type)));
                    }
                    records.add(new UBRecord(ls.val, off, ls.base));
                } else {
                    // 写个简单版的，直接全清空就没事了
                    records.clear();
                }
            } else if (instr instanceof J) {
                records.clear();
            } else {
                for (int i = 0; i < instr.getOperandNum(); i++) {
                    if (instr.isDef(i)) {
                        Reg reg = instr.getRegByIdx(i);
                        removeByReg(reg);
                        removeByBase(reg);
                    }
                }
            }
        }
        for (Pair<LS, R2> entry : ls2move) {
            block.insertInstBefore(entry.second, entry.first);
            entry.first.remove();
        }
    }
}
