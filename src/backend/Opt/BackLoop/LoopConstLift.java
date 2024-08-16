package backend.Opt.BackLoop;

import backend.Opt.GPpooling.GlobalFloat2roPool;
import backend.Opt.MemoryOpt.UnknownBaseLSOpt;
import backend.allocator.LivenessAnalyze;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;

import java.util.ArrayList;

import static backend.Opt.MemoryOpt.UnknownBaseLSOpt.records;

public class LoopConstLift {
    // 专属于循环不变量外提
    // 一般属于la与li,剩下的其实都是算循环变量了,都已经在外面实现了
    // 其实剩下的还有从pool中提取数值,这也算循环不变量

    private static int temp_cnt = 0;

    private static final boolean lift_flw_pool = true;

    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            LivenessAnalyze.RunOnFunc(function);
            runOnFunc(function);
        }
    }

    // 需要在函数的尺度新建中转块
    private static void runOnFunc(RiscvFunction function) {
        for (RiscLoop loop : function.loops) {
            while (true) {
                if (!checkOnLoop(function, loop)) break;
            }
        }
        LivenessAnalyze.RunOnFunc(function);
        for (RiscLoop loop : function.loops) {
            runOnLoop(loop);
        }
        for (RiscvBlock block : function.blocks) {
            runOnBlockBefRA(block);
        }
    }

    private static boolean checkOnLoop(RiscvFunction function, RiscLoop riscLoop) {
        for (RiscvBlock block : riscLoop.enterings) {
            if (!(block.riscvInstructions.getLast() instanceof J j)) {
                throw new RuntimeException("wrong type");
            }
            if (!riscLoop.blocks.contains(j.targetBlock)) {
                // 对于所有entering,如果有不是直接j到这里的,那么就不可以直接进行外提
                //检查一下b
                for (RiscvInstruction ri : block.riscvInstructions) {
                    if (ri instanceof B b && riscLoop.blocks.contains(b.targetBlock)) {
                        // 如果满足这个情况,就需要新建一个小的块,然后更换enterings
                        RiscvBlock newBlock = new RiscvBlock(function, "lft_temp_" + temp_cnt++);
                        function.blocks.add(newBlock);
                        riscLoop.enterings.remove(block);
                        riscLoop.enterings.add(newBlock);
                        newBlock.addInstLast(new J(newBlock, J.JType.j, b.targetBlock));
                        newBlock.preBlock.add(block);
                        newBlock.succBlock.add(b.targetBlock);
                        b.targetBlock = newBlock;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void runOnLoop(RiscLoop riscLoop) {
        ArrayList<RiscvInstruction> arrayList = new ArrayList<>();
        for (RiscvBlock block : riscLoop.blocks) {
            Reg poolContainer = null;
            for (RiscvInstruction ri : block.riscvInstructions) {
                if (lift_flw_pool) {
                    if (ri instanceof La || ri instanceof Li ||
                            (ri instanceof LS ls && ls.type == LS.LSType.flw && ls.base.equals(poolContainer))) {
                        arrayList.add(ri);
                        if (ri instanceof La la && la.content.equals(GlobalFloat2roPool.gPpool)) {
                            if (poolContainer == null) {
                                poolContainer = la.reg;
                            } else {
                                poolContainer.mergeReg(la.reg);
                            }
                        }
                    }
                } else {
                    if (ri instanceof La || ri instanceof Li) {
                        arrayList.add(ri);
                    }
                }
            }
        }
        // 是否存在J指向的不是这个循环的情况
        for (RiscvBlock block : riscLoop.enterings) {
            if (!(block.riscvInstructions.getLast() instanceof J j)) {
                throw new RuntimeException("wrong type");
            }
            for (RiscvInstruction ri : arrayList) {
                block.insertInstBefore(ri.myCopy(block), j);
            }
        }
        for (RiscvInstruction ri : arrayList) {
            ri.remove();
        }
        for (RiscLoop subLoop : riscLoop.subLoops) {
            runOnLoop(subLoop);
        }
    }


    private static void runOnBlockBefRA(RiscvBlock block) {
        ArrayList<LS> ls2move = new ArrayList<>();
        for (RiscvInstruction instr : block.riscvInstructions) {
            if (instr instanceof LS ls && ls.base.phyReg != Reg.PhyReg.sp) {
                long off = ((Imm) ls.addr).getVal();
                if (ls.type == LS.LSType.ld || ls.type == LS.LSType.lw || ls.type == LS.LSType.flw) {
                    ArrayList<UnknownBaseLSOpt.UBRecord> list = UnknownBaseLSOpt.queryByOff(ls.val, ls.base, off);
                    UnknownBaseLSOpt.removeByReg(ls.val);
                    UnknownBaseLSOpt.removeByBase(ls.val);
                    if (!list.isEmpty()) {
                        ls2move.add(ls);
                        if (!list.get(0).getReg().equals(ls.val)) list.get(0).getReg().mergeReg(ls.val);
                    } else {
                        records.add(new UnknownBaseLSOpt.UBRecord(ls.val, off, ls.base));
                    }
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
                        UnknownBaseLSOpt.removeByReg(reg);
                        UnknownBaseLSOpt.removeByBase(reg);
                    }
                }
            }
        }
        for (LS ls : ls2move) {
            ls.remove();
        }
    }
}