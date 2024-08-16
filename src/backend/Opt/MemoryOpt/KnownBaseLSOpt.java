package backend.Opt.MemoryOpt;

import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Operand;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class KnownBaseLSOpt {
    // 应当在块内联后做，因为块比较大，所以做起来效果更好
    // 删除不必要的指令或者将lw换成mv
    // 记录了全局基址指针对应的寄存器
    // 无法计算经过gep得到的指针,遇到这个sw的情况就要清空所有记录

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }


    private static final HashMap<Reg, RiscvGlobalVar> baseRecords = new HashMap<>();
    private static final ArrayList<LSRecord> records = new ArrayList<>();

    //覆盖所有有对应地址信息的记录
    private static void coverByOffset(LSRecord record) {
        // 如果这个新的是sp的
        records.removeIf(cord -> cord.offset == record.offset && cord.base.equals(record.base));
    }


    private static void coverByReg(Reg reg) {
        records.removeIf(spRecord -> spRecord.reg.equals(reg));
    }


    private static ArrayList<LSRecord> queryByOff(Reg bestReg, Operand base, long offset) {
        ArrayList<LSRecord> ans = new ArrayList<>();
        for (LSRecord br : records) {
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

    private static ArrayList<LSRecord> queryByReg(Reg reg) {
        ArrayList<LSRecord> ans = new ArrayList<>();
        for (LSRecord br : records) {
            if (br.reg.equals(reg)) {
                ans.add(br);
            }
        }
        return ans;
    }

    private static boolean hasSame(LSRecord record) {
        for (LSRecord ls : records) {
            if (record.reg.equals(ls.reg) && record.offset == ls.offset && record.base.equals(ls.base))
                return true;
        }
        return false;
    }


    private static class LSRecord {
        private final Reg reg;
        private final long offset;

        private final Operand base;

        public LSRecord(Reg reg, long off, Operand base) {
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

        public Operand getBase() {
            return base;
        }

        public LSRecord myCopy(Reg newReg) {
            return new LSRecord(newReg, offset, base);
        }

    }


    private static void runOnBlock(RiscvBlock block) {
        baseRecords.clear();
        records.clear();
        Iterator<RiscvInstruction> iterator = block.riscvInstructions.iterator();
        ArrayList<Pair<LS, R2>> ls2move = new ArrayList<>();
        while (iterator.hasNext()) {
            RiscvInstruction instr = iterator.next();
            // 如果是la,那么就会将一个global放到一个寄存器里面
            if (instr instanceof La la) {
                baseRecords.put(la.reg, la.content);
                coverByReg(la.reg);
            } else if (instr instanceof R2 r2 && r2.type == R2.R2Type.mv) {
                // 如果存在一个move，那么可以将原有的record复制
                if (r2.rd.equals(r2.rs)) continue;
                coverByReg((Reg) r2.rd);
                baseRecords.remove((Reg) r2.rd);
                ArrayList<LSRecord> list = queryByReg((Reg) r2.rs);
                // 将所有的该寄存器对应的记录复制
                for (LSRecord tmp : list) {
                    records.add(tmp.myCopy((Reg) r2.rd));
                }
                if (baseRecords.containsKey((Reg) r2.rs)) {
                    baseRecords.put((Reg) r2.rd, baseRecords.get((Reg) r2.rs));
                }
            } else if (instr instanceof J) {
                records.clear();
                baseRecords.clear();
            } else if (!(instr instanceof LS ls)) {
                // 不属于ls的话,则需要检查它的def，将所有def的寄存器的record全部清空
                for (int i = 0; i < instr.getOperandNum(); i++) {
                    if (instr.isDef(i)) {
                        Reg reg = instr.getRegByIdx(i);
                        baseRecords.remove(reg);
                        coverByReg(reg);
                    }
                }
            } else {
                // 证明是ls
                LS.LSType type = ls.type;
                Reg base = ls.base;
                if (type == LS.LSType.ld || type == LS.LSType.lw || type == LS.LSType.flw) {
                    // 如果是lw,且不存在这样的记录,那么更新这个
                    if (base.phyReg == Reg.PhyReg.sp) {
                        // 如果是sp
                        // 首先清空所有和这个被寄存器相关的
                        ArrayList<LSRecord> list = queryByOff(ls.val, base, ((Address) ls.addr).getOffset());
                        coverByReg(ls.val);
                        // 如果找到了,那么说明该地址对应的值已经存起来了,直接move即可
                        // 同时需要保证如果是原本有的话，也能将记录补上
                        if (!list.isEmpty()) {
                            R2.R2Type r2Type = ls.val.regType == Reg.RegType.GPR ? R2.R2Type.mv : R2.R2Type.fmv;
                            ls2move.add(new Pair<>(ls, new R2(block, ls.val, list.get(0).reg, r2Type)));
                        }
                        records.add(new LSRecord(ls.val, ((Address) ls.addr).getOffset(), base));
                    } else if (baseRecords.containsKey(base)) {
                        // 如果是全局的
                        RiscvGlobalVar rb = baseRecords.get(base);
                        ArrayList<LSRecord> list = queryByOff(ls.val, rb, ((Imm) ls.addr).getVal());
                        coverByReg(ls.val);
                        // 如果找到了,那么说明该地址对应的值已经存起来了,直接move即可
                        // 同时需要保证如果是原本有的话，也能将记录补上
                        if (!list.isEmpty()) {
                            R2.R2Type r2Type = ls.val.regType == Reg.RegType.GPR ? R2.R2Type.mv : R2.R2Type.fmv;
                            ls2move.add(new Pair<>(ls, new R2(block, ls.val, list.get(0).reg, r2Type)));
                        }
                        records.add(new LSRecord(ls.val, ((Imm) ls.addr).getVal(), rb));
                    } else {
                        // 既不是全局的,也不是sp,那么就不知道获取的是来自哪里的了，就没法分析了
                        // 因此也要将这个有关的记录全部删除
                        coverByReg(ls.val);
                    }
                } else {
                    // 如果是sw系列的
                    if (ls.base.phyReg == Reg.PhyReg.sp) {
                        // 如果是sp，如果有一模一样的记录的话那就删了这个指令,否则要清空这个地址的所有记录
                        LSRecord lsRecord = new LSRecord(ls.val, ((Address) ls.addr).getOffset(), base);
                        if (hasSame(lsRecord)) {
                            iterator.remove();
                        } else {
                            coverByOffset(lsRecord);
                            records.add(lsRecord);
                        }
                    } else if (baseRecords.containsKey(base)) {
                        // 如果是全局的
                        RiscvGlobalVar rb = baseRecords.get(base);
                        LSRecord lsRecord = new LSRecord(ls.val, ((Imm) ls.addr).getVal(), rb);
                        if (hasSame(lsRecord)) {
                            iterator.remove();
                        } else {
                            coverByOffset(lsRecord);
                            records.add(lsRecord);
                        }
                    } else {
                        // 如果都不是,那么就不能知晓是来自哪里的,这时候就不知道所有记录的值是否是新的，因此要清空所有记录
                        records.clear();
                    }
                }
                baseRecords.remove(ls.val);
            }
        }
        for (Pair<LS, R2> entry : ls2move) {
            block.insertInstBefore(entry.second, entry.first);
            entry.first.remove();
        }
    }

}
