package backend.Opt;

import backend.Ir2RiscV.VirRegMap;
import backend.allocator.LivenessAnalyze;
import backend.operand.Address;
import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import midend.Analysis.AlignmentAnalysis;
import utils.NelLinkedList;
import utils.Pair;

import java.util.*;

public class CalculateOpt {
    public static void runBeforeRA(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            LivenessAnalyze.RunOnFunc(function);
            for (RiscvBlock block : function.blocks) {
                uselessLoadRemove(block);
                PreRAConstValueReUse(block);
                PreRAConstImmCalReuse(block);
                PreRAConstPointerReUse(block);
                addZero2Mv(block);
                addiLS2LSoffset(block);
                One2ZeroBeq(block);
//                seqzBranchReverse(block);
            }
        }
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                icmpBranchToBranch(block);
                SraSll2And(block);
                Lsw2Lsd(block);
                Li2Lui(block);
            }
        }
    }

    private static void seqzBranchReverse(RiscvBlock block) {
        Iterator<RiscvInstruction> iterator = block.riscvInstructions.iterator();
        while (iterator.hasNext()) {
            RiscvInstruction ri = iterator.next();
            if (ri instanceof R2 r2 && r2.type == R2.R2Type.seqz) {
                HashSet<RiscvInstruction> used = LivenessAnalyze.RegUse.get((Reg) r2.rd);
                boolean canReplace = true;
                for (RiscvInstruction user : used) {
                    if (!(user instanceof B b && ((Reg) b.rs2).phyReg == Reg.PhyReg.zero
                            && (b.type == B.BType.bne || b.type == B.BType.beq))) {
                        canReplace = false;
                        break;
                    }
                }
                if (canReplace) {
                    iterator.remove();
                    for (RiscvInstruction next : used) {
                        if (next instanceof B b && ((Reg) b.rs2).phyReg == Reg.PhyReg.zero) {
                            if (b.type == B.BType.bne) {
                                b.type = B.BType.beq;
                                b.rs1 = r2.rs;
                            } else if (b.type == B.BType.beq) {
                                b.type = B.BType.bne;
                                b.rs1 = r2.rs;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void SraSll2And(RiscvBlock block) {
        ArrayList<Pair<Pair<R3, R3>, ArrayList<RiscvInstruction>>> needRemove = new ArrayList<>();
        for (int i = 0; i < block.riscvInstructions.size() - 1; i++) {
            if (block.riscvInstructions.get(i) instanceof R3 r1 && r1.type == R3.R3Type.sraiw) {
                if (block.riscvInstructions.get(i + 1) instanceof R3 r2 && r2.type == R3.R3Type.slliw) {
                    if (((Imm) r1.rs2).getVal() == ((Imm) r2.rs2).getVal()) {
                        if (r1.rd.equals(r2.rs1)) {
                            int ans = -(1 << ((Imm) r2.rs2).getVal());
                            if (ans >= -2047) {
                                var pair = new Pair<>(new Pair<>(r1, r2), new ArrayList<RiscvInstruction>());
                                pair.second.add(new R3(block, r2.rd, r1.rs1, new Imm(ans), R3.R3Type.andi));
                                needRemove.add(pair);
                            }
                        }
                    }
                }
            }
        }
        for (var pair : needRemove) {
            for (RiscvInstruction ri : pair.second) {
                block.insertInstBefore(ri, pair.first.first);
            }
            pair.first.first.remove();
            pair.first.second.remove();
        }
    }

    // 将addi+ls匹配成直接写的偏移
    private static void addiLS2LSoffset(RiscvBlock block) {
        HashSet<R3> needRemove = new HashSet<>();
        for (int i = 0; i < block.riscvInstructions.size(); i++) {
            if (block.riscvInstructions.get(i) instanceof R3 addi && addi.type == R3.R3Type.addi) {
                boolean canRemove = true;
                HashSet<RiscvInstruction> instrs = LivenessAnalyze.RegUse.get((Reg) addi.rd);
                for (RiscvInstruction inst : instrs) {
                    if (inst instanceof LS ls) {
                        if (ls.base.equals(addi.rd) && ls.addr instanceof Imm imm && imm.getVal() == 0 && !ls.val.equals(addi.rd)) {
                            ls.base = (Reg) addi.rs1;
                            ls.addr = new Imm(((Imm) addi.rs2).getVal());
                            if (ls.align == AlignmentAnalysis.AlignType.ALIGN_BYTE_8) {
                                if (((Imm) addi.rs2).getVal() % 8 != 0) {
                                    ls.align = AlignmentAnalysis.AlignType.ALIGN_BYTE_4;
                                }
                            } else if (ls.align == AlignmentAnalysis.AlignType.ALIGN_BYTE_4) {
                                if (((Imm) addi.rs2).getVal() % 8 != 0) {
                                    ls.align = AlignmentAnalysis.AlignType.ALIGN_BYTE_8;
                                }
                            }
                        } else if (ls.val.equals(addi.rd)) {
                            canRemove = false;
                        }
                    } else {
                        // 如果是ls的话但是使用了这个呢?
                        if (LivenessAnalyze.Use.get(inst).contains((Reg) addi.rd)) {
                            canRemove = false;
                        }
                    }
                }
                if (canRemove) {
                    needRemove.add(addi);
                }
            }
        }
        needRemove.forEach(NelLinkedList.NelLinkNode::remove);
    }

    // 重要依据，这些拉取的常数和全局的不会跨过块

    private static void uselessLoadRemove(RiscvBlock block) {
        HashSet<RiscvInstruction> needRemove = new HashSet<>();
        for (RiscvInstruction instr : block.riscvInstructions) {
            if (instr == block.riscvInstructions.getFirst()) continue;
            RiscvInstruction before = (RiscvInstruction) instr.prev;
            if (before instanceof LS be && instr instanceof LS af) {
                if (be.val.equals(af.val) && be.base.equals(af.base) && be.addr.equals(af.addr)) {
                    needRemove.add(instr);
                }
            }
        }
        for (RiscvInstruction ri : needRemove) {
            ri.remove();
        }
    }

    /**
     * >-1:>=0
     * <1:<=0
     * 1>:0>=
     * -1<\:0<=
     **/
    private static void One2ZeroBeq(RiscvBlock block) {
        HashMap<Pair<Li, B>, B> need = new HashMap<>();
        for (int i = 0; i < block.riscvInstructions.size() - 1; i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            RiscvInstruction next = block.riscvInstructions.get(i + 1);
            if (now instanceof Li li && li.imm instanceof Imm imm && next instanceof B b) {
                if (imm.getVal() == 1 && b.rs1.equals(li.reg) && b.type == B.BType.bgt) {
                    need.put(new Pair<>(li, b),
                            new B(block, B.BType.bge, Reg.getPreColoredReg(Reg.PhyReg.zero, 32),
                                    b.rs2, b.targetBlock, b.getYesProb()));
                } else if (imm.getVal() == 1 && b.rs2.equals(li.reg) && b.type == B.BType.blt) {
                    need.put(new Pair<>(li, b),
                            new B(block, B.BType.ble, b.rs1,
                                    Reg.getPreColoredReg(Reg.PhyReg.zero, 32), b.targetBlock, b.getYesProb()));
                } else if (imm.getVal() == -1 && b.rs2.equals(li.reg) && b.type == B.BType.bgt) {
                    need.put(new Pair<>(li, b),
                            new B(block, B.BType.bge, b.rs1, Reg.getPreColoredReg(Reg.PhyReg.zero, 32),
                                    b.targetBlock, b.getYesProb()));
                } else if (imm.getVal() == -1 && b.rs1.equals(li.reg) && b.type == B.BType.blt) {
                    need.put(new Pair<>(li, b),
                            new B(block, B.BType.ble, Reg.getPreColoredReg(Reg.PhyReg.zero, 32), b.rs2,
                                    b.targetBlock, b.getYesProb()));
                }
            }
        }
        for (var pair : need.keySet()) {
            block.insertInstBefore(need.get(pair), pair.first);
            pair.first.remove();
            pair.second.remove();
        }
    }

    /**
     * 警惕！：可能会出现位扩展不对的情况
     **/
    private static void addZero2Mv(RiscvBlock block) {
        ArrayList<Pair<R3, R2>> needReplace = new ArrayList<>();
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof R3 r3 && (((R3) ri).type == R3.R3Type.addw || ((R3) ri).type == R3.R3Type.add)) {
                if (((Reg) r3.rs2).phyReg == Reg.PhyReg.zero) {
                    needReplace.add(new Pair<>(r3,
                            new R2(block, ((R3) ri).rd, ((R3) ri).rs1, R2.R2Type.mv)));
                } else if (((Reg) r3.rs1).phyReg == Reg.PhyReg.zero) {
                    needReplace.add(new Pair<>(r3,
                            new R2(block, ((R3) ri).rd, ((R3) ri).rs2, R2.R2Type.mv)));
                }
            }
        }
        for (var pair : needReplace) {
            block.insertInstBefore(pair.second, pair.first);
            pair.first.remove();
        }
    }

    private static boolean matchEQ(RiscvInstruction now, RiscvInstruction next, RiscvInstruction farNext) {
        if (now instanceof R3 && ((R3) now).type == R3.R3Type.subw && next instanceof R2 &&
                ((R2) next).type == R2.R2Type.seqz && farNext instanceof B) {
            return ((R3) now).rd.equals(((R2) next).rs) &&
                    (((R2) next).rd.equals(((B) farNext).rs1) || ((R2) next).rd.equals(((B) farNext).rs2));
        }
        return false;
    }

    private static boolean matchNE(RiscvInstruction now, RiscvInstruction next, RiscvInstruction farNext) {
        if (now instanceof R3 && ((R3) now).type == R3.R3Type.subw && next instanceof R2 &&
                ((R2) next).type == R2.R2Type.snez && farNext instanceof B) {
            return ((R3) now).rd.equals(((R2) next).rs) &&
                    (((R2) next).rd.equals(((B) farNext).rs1) || ((R2) next).rd.equals(((B) farNext).rs2));
        }
        return false;
    }

    private static boolean matchSGEAndSLE(RiscvInstruction now, RiscvInstruction next, RiscvInstruction farNext) {
        if (now instanceof R3 && ((R3) now).type == R3.R3Type.slt && next instanceof R2 &&
                ((R2) next).type == R2.R2Type.seqz && farNext instanceof B) {
            return ((R3) now).rd.equals(((R2) next).rs) &&
                    (((R2) next).rd.equals(((B) farNext).rs1) || ((R2) next).rd.equals(((B) farNext).rs2));
        }
        return false;
    }

    private static boolean matchSLTAndSGT(RiscvInstruction now, RiscvInstruction next) {
        if (now instanceof R3 && ((R3) now).type == R3.R3Type.slt && next instanceof B) {
            return ((R3) now).rd.equals(((B) next).rs2) || (((R3) now).rd.equals(((B) next).rs1));
        }
        return false;
    }

    private static boolean matchFarNext(RiscvInstruction now, RiscvInstruction next, RiscvInstruction farNext) {
        return matchNE(now, next, farNext) || matchEQ(now, next, farNext) || matchSGEAndSLE(now, next, farNext);
    }


    // 将icmp和branch合并
    // 这里的原本的B可能是bne也可能是beq,需要特殊判定
    private static void icmpBranchToBranch(RiscvBlock block) {
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < block.riscvInstructions.size(); i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            boolean modified = false;
            if (i < block.riscvInstructions.size() - 1) {
                RiscvInstruction next = block.riscvInstructions.get(i + 1);
                if (matchSLTAndSGT(now, next)) {
                    //slt ,置1，bne r,zero,不为0则跳转,所以就是小于则跳转,beq r,zero,就是大于等于则跳转
                    Reg reg = (Reg) ((R3) now).rd;
                    if (VirRegMap.bUseReg.get(reg) <= 1) {
                        double prob = ((B) next).getYesProb();
                        if (((B) next).type == B.BType.bne) {
                            newList.add(new B(block, B.BType.blt, ((R3) now).rs1,
                                    (((R3) now).rs2), ((B) next).targetBlock, prob));
                        } else {
                            newList.add(new B(block, B.BType.bge, ((R3) now).rs1,
                                    (((R3) now).rs2), ((B) next).targetBlock, prob));
                        }
                        i++;
                        // 将next忽略
                        modified = true;
                    }
                } else if (i < block.riscvInstructions.size() - 2) {
                    RiscvInstruction farNext = block.riscvInstructions.get(i + 2);
                    if (matchFarNext(now, next, farNext)) {
                        B.BType type;
                        boolean reverse = ((B) farNext).type == B.BType.beq;
                        if (matchEQ(now, next, farNext)) {
                            // subw,seqz,看看是不是0
                            type = reverse ? B.BType.bne : B.BType.beq;
                        } else if (matchNE(now, next, farNext)) {
                            type = reverse ? B.BType.beq : B.BType.bne;
                        } else if (matchSGEAndSLE(now, next, farNext)) {
                            // 这个存起来就看后面的是不是大于等于前面的
                            type = reverse ? B.BType.blt : B.BType.bge;
                        } else throw new RuntimeException("wrong match");
                        assert next instanceof R2;
                        Reg reg = (Reg) ((R2) next).rd;
                        if (VirRegMap.bUseReg.get(reg) <= 1) {
                            double prob = ((B) farNext).getYesProb();
                            newList.add(new B(block, type, ((R3) now).rs1, ((R3) now).rs2, ((B) farNext).targetBlock, prob));
                            i = i + 2;
                            modified = true;
                        }
                    }
                }
            }
            if (!modified) {
                newList.add(now);
            }
        }
        block.riscvInstructions.clear();
        for (RiscvInstruction ri : newList) {
            block.addInstLast(ri);
        }
    }

    private static void PreRAConstValueReUse(RiscvBlock riscvBlock) {
        final int range = 16;
        HashMap<Long, Pair<Reg, Integer>> map = new HashMap<>();
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < riscvBlock.riscvInstructions.size(); i++) {
            RiscvInstruction instr = riscvBlock.riscvInstructions.get(i);
            for (int idx = 0; idx < instr.getOperandNum(); idx++) {
                if (instr.isDef(idx) && !(instr instanceof Li)) {
                    int finalIdx = idx;
                    map.keySet().removeIf(key -> map.get(key).first.equals(instr.getRegByIdx(finalIdx)));
                }//删除所有重定义的
            }
            if (instr instanceof Li) {
                // 如果前面有记录这个值，那么就将这个li给删掉，然后将后面的所有使用这个的寄存器都换掉
                long value = ((Li) instr).getVal();
                if (map.containsKey(value)) {
                    Reg now = map.get(value).first;
                    Reg def = ((Li) instr).reg;
                    if (!now.equals(def)) now.mergeReg(def);
                    map.get(value).second = range;// 刷新生存周期
                    continue;
                } else {
                    map.keySet().removeIf(key -> map.get(key).first.equals(((Li) instr).reg));
                    map.put(value, new Pair<>(((Li) instr).reg, range));
                }
            }
            newList.add(instr);
            HashSet<Long> needRemove = new HashSet<>();
            for (Long key : map.keySet()) {
                map.get(key).second--;
                if (map.get(key).second == 0) {
                    needRemove.add(key);
                }
            }
            for (Long need : needRemove) {
                map.remove(need);
            }
        }
        riscvBlock.riscvInstructions.clear();
        for (RiscvInstruction ri : newList) {
            riscvBlock.addInstLast(ri);
        }
    }

    private static void Li2Lui(RiscvBlock block) {
        HashMap<Li, Lui> needReplace = new HashMap<>();
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof Li li) {
                long value = li.getVal();
                if (value >= 0 && value <= Integer.MAX_VALUE && ((value >> 12) << 12) == value) {
                    needReplace.put(li, new Lui(block, li.reg, new Imm(li.getVal() >> 12)));
                }
            }
        }
        for (Li key : needReplace.keySet()) {
            block.insertInstBefore(needReplace.get(key), key);
            key.remove();
        }
    }

    private static void PreRAConstImmCalReuse(RiscvBlock block) {
        Queue<Pair<Reg, Long>> queue = new LinkedList<>();
        final int windowsize = 4;
        HashMap<Li, RiscvInstruction> needReplace = new HashMap<>();
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof Li now) {
                long imm = now.getVal();
                // 先判断是否需要加立即数
                boolean modify = false;
                for (var pair : queue) {
                    if (imm == pair.second) {
                        needReplace.put(now, new R2(block, now.reg, pair.first, R2.R2Type.mv));
                        modify = true;
                        break;
                    } else if (imm - pair.second <= 2047 && imm - pair.second >= -2047) {
                        needReplace.put(now, new R3(block, now.reg, pair.first, new Imm(imm - pair.second), R3.R3Type.addiw));
                        modify = true;
                        break;
                    } else if (-imm == pair.second) {
                        needReplace.put(now, new R3(block, now.reg, Reg.getPreColoredReg(Reg.PhyReg.zero, 64), pair.first, R3.R3Type.sub));
                        modify = true;
                        break;
                    } else if (~imm == pair.second) {
                        needReplace.put(now, new R3(block, now.reg, pair.first, new Imm(-1), R3.R3Type.xori));
                        modify = true;
                        break;
                    } else if (imm == 3 * pair.second) {
                        needReplace.put(now, new R3(block, now.reg, pair.first, pair.first, R3.R3Type.sh1add));
                        modify = true;
                        break;
                    } else if (imm == 5 * pair.second) {
                        needReplace.put(now, new R3(block, now.reg, pair.first, pair.first, R3.R3Type.sh2add));
                        modify = true;
                        break;
                    } else if (imm == 9 * pair.second) {
                        needReplace.put(now, new R3(block, now.reg, pair.first, pair.first, R3.R3Type.sh3add));
                        modify = true;
                        break;
                    } else {
                        int maxK = 8;
                        for (int k = 1; k <= maxK; k++) {
                            if (imm == pair.second << k) {
                                needReplace.put(now, new R3(block, now.reg, pair.first, new Imm(k), R3.R3Type.slliw));
                                modify = true;
                                break;
                            } else if (imm == pair.second >> k) {
                                needReplace.put(now, new R3(block, now.reg, pair.first, new Imm(k), R3.R3Type.srliw));
                                modify = true;
                                break;
                            }
                        }
                        if (modify) {
                            break;
                        }
                    }
                }
                if (!modify) {
                    queue.add(new Pair<>(now.reg, imm));
                    while (queue.size() > windowsize) {
                        queue.poll();
                    }
                }
            }
        }
        for (var pair : needReplace.entrySet()) {
            block.insertInstBefore(pair.getValue(), pair.getKey());
            pair.getKey().remove();
        }
    }

    private static void PreRAConstPointerReUse(RiscvBlock riscvBlock) {
        final int range = 16;
        HashMap<RiscvGlobalVar, Pair<Reg, Integer>> map = new HashMap<>();
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < riscvBlock.riscvInstructions.size(); i++) {
            RiscvInstruction instr = riscvBlock.riscvInstructions.get(i);
            for (int idx = 0; idx < instr.getOperandNum(); idx++) {
                if (instr.isDef(idx) && !(instr instanceof La)) {
                    int finalIdx = idx;
                    map.keySet().removeIf(key -> map.get(key).first.equals(instr.getRegByIdx(finalIdx)));
                }//删除所有重定义的
            }
            if (instr instanceof La) {
                // 如果前面有记录这个值，那么就将这个li给删掉，然后将后面的所有使用这个的寄存器都换掉
                if (map.containsKey((((La) instr).content))) {
                    Reg now = map.get(((La) instr).content).first;
                    Reg def = ((La) instr).reg;
                    if (!now.equals(def)) now.mergeReg(def);
                    map.get(((La) instr).content).second = range;// 刷新生存周期
                    continue;
                } else {
                    map.keySet().removeIf(key -> map.get(key).first.equals(((La) instr).reg));
                    map.put(((La) instr).content, new Pair<>(((La) instr).reg, range));
                }
            }
            newList.add(instr);
            HashSet<RiscvGlobalVar> needRemove = new HashSet<>();
            for (RiscvGlobalVar key : map.keySet()) {
                map.get(key).second--;
                if (map.get(key).second == 0) {
                    needRemove.add(key);
                }
            }
            for (RiscvGlobalVar need : needRemove) {
                map.remove(need);
            }
        }
        riscvBlock.riscvInstructions.clear();
        for (RiscvInstruction ri : newList) {
            riscvBlock.addInstLast(ri);
        }
    }

    // 在块内联后使用,此时已经分配好了寄存器
    // 在地址opt后，此时已经解决了全局指针的复用，也就是只需要解决全局的li即可
    // 还有同寄存器mv
    public static void runAftBin(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                AftBinConstValueReuse(block);
                removeSameMv(block);
                subZeroRemove(block);
            }
        }
    }

    private static void subZeroRemove(RiscvBlock block) {
        Iterator<RiscvInstruction> iterator = block.riscvInstructions.iterator();
        while (iterator.hasNext()) {
            RiscvInstruction ri = iterator.next();
            if (ri instanceof R3 r3 && (r3.type == R3.R3Type.sub || r3.type == R3.R3Type.subw)) {
                if (r3.rd.equals(r3.rs1) && r3.rs2 instanceof Reg reg && reg.phyReg == Reg.PhyReg.zero) {
                    iterator.remove();
                }
            }
        }
    }

    private static void removeSameMv(RiscvBlock riscvBlock) {
        Iterator<RiscvInstruction> iterator = riscvBlock.riscvInstructions.iterator();
        while (iterator.hasNext()) {
            RiscvInstruction ri = iterator.next();
            if (ri instanceof R2 r2 && (r2.type == R2.R2Type.mv || r2.type == R2.R2Type.fmv)) {
                if (r2.rd.equals(r2.rs)) {
                    iterator.remove();
                }
            }
        }
    }

    private static final HashMap<Reg, Long> re2Int = new HashMap<>();

    private static void mvCopy(Reg bef, Reg aft) {
        if (bef.equals(aft)) return;
        if (!re2Int.containsKey(bef)) return;
        long val = re2Int.get(bef);
        re2Int.put(aft, val);
    }

    private static Reg liFind(Reg bestReg, long value) {
        if (re2Int.containsKey(bestReg) && re2Int.get(bestReg) == value) return bestReg;
        else {
            for (Map.Entry<Reg, Long> entry : re2Int.entrySet()) {
                if (entry.getValue() == value) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static void AftBinConstValueReuse(RiscvBlock block) {
        ArrayList<Pair<Li, R2>> needReplace = new ArrayList<>();
        for (RiscvInstruction ri : block.riscvInstructions) {
            if (ri instanceof R2 r2 && ((R2) ri).type == R2.R2Type.mv) {
                if (r2.rd.equals(r2.rs)) continue;
                re2Int.remove((Reg) r2.rd);
                mvCopy((Reg) r2.rs, (Reg) r2.rd);
            } else if (ri instanceof Li li) {
                Reg same = liFind(li.reg, li.getVal());
                if (same == null) {
                    re2Int.put(li.reg, li.getVal());
                } else {
                    needReplace.add(new Pair<>(li, new R2(block, li.reg, same, R2.R2Type.mv)));
                    mvCopy(same, li.reg);
                }
            } else if (ri instanceof J) {
                re2Int.clear();
            } else {
                for (int i = 0; i < ri.getOperandNum(); i++) {
                    if (ri.isDef(i)) {
                        Reg reg = ri.getRegByIdx(i);
                        re2Int.remove(reg);
                    }
                }
            }
        }
        for (Pair<Li, R2> pair : needReplace) {
            block.insertInstBefore(pair.second, pair.first);
            pair.first.remove();
        }
    }

    private static boolean lsConflict(RiscvBlock block, int i, int j, boolean checkInLw, Reg base, long offset) {
        // 对于ij之间的指令,check是否有覆盖的相应的内容
        for (int k = i + 1; k < j; k++) {
            RiscvInstruction ri = block.riscvInstructions.get(i);
            if (ri instanceof LS s && s.base.equals(base)) {
                if (s.addr instanceof Imm || s.addr instanceof Address a && a.hasFilled()) {
                    long off = s.addr instanceof Address a ? a.getOffset() : ((Imm) s.addr).getVal();
                    if (checkInLw) {
                        // 如果是lw，检查它是否是sw且覆写了后半段
                        if (s.type == LS.LSType.sw || s.type == LS.LSType.sd || s.type == LS.LSType.fsw) {
                            return off == offset + 4;
                        }
                    } else {
                        if (s.type == LS.LSType.lw || s.type == LS.LSType.ld || s.type == LS.LSType.flw) {
                            return off == offset;
                        }
                    }
                }
            } else if (ri instanceof J jjj && jjj.type == J.JType.call) {
                return false;
            }
        }
        return false;
    }

    private static void Lsw2Lsd(RiscvBlock block) {
        HashMap<LS, ArrayList<RiscvInstruction>> needReplace = new HashMap<>();
        for (int i = 0; i < block.riscvInstructions.size(); i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            if (now instanceof LS ls1 && !needReplace.containsKey(ls1)) {
                for (int j = i + 1; j < block.riscvInstructions.size(); j++) {
                    if (needReplace.containsKey(ls1)) break;
                    RiscvInstruction next = block.riscvInstructions.get(j);
                    if (next instanceof LS ls2 && !needReplace.containsKey(ls2)) {
                        if (ls1.base.equals(ls2.base)) {
                            if ((ls1.addr instanceof Imm imm1 && ls2.addr instanceof
                                    Imm imm2 && imm2.getVal() - imm1.getVal() == 4) || (
                                    ls1.addr instanceof Address a1 && a1.hasFilled()
                                            && ls2.addr instanceof Address a2 && a2.hasFilled() &&
                                            a1.getOffset() - a2.getOffset() == 4)) {
                                long off = ls1.addr instanceof Imm imm1 ? imm1.getVal() : ((Address) ls1.addr).getOffset();
                                boolean can = (off % 8 == 0 && ls1.align == AlignmentAnalysis.AlignType.ALIGN_BYTE_8) ||
                                        (off % 8 != 0 && ls1.align == AlignmentAnalysis.AlignType.ALIGN_BYTE_4);
                                if (!can) break;
                                AlignmentAnalysis.AlignType alignType = off % 8 == 0 ? AlignmentAnalysis.AlignType.ALIGN_BYTE_8 : AlignmentAnalysis.AlignType.ALIGN_BYTE_4;
                                if (ls1.type == LS.LSType.sw && ls2.type == LS.LSType.sw) {
                                    //如果是俩zero
                                    if (lsConflict(block, i, j, false, ls1.base, off)) break;
                                    ArrayList<RiscvInstruction> list = new ArrayList<>();
                                    if (ls1.val.preColored && ls1.val.phyReg == Reg.PhyReg.zero
                                            && ls2.val.preColored && ls2.val.phyReg == Reg.PhyReg.zero) {
                                        list.add(new LS(block, Reg.getPreColoredReg(Reg.PhyReg.zero, 64),
                                                ls2.base, ls1.addr, LS.LSType.sd, AlignmentAnalysis.AlignType.ALIGN_BYTE_4));
                                    } else {
                                        //如果不是俩zero,那么就需要sli,or然后sd
                                        Reg reg = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                                        list.add(new R3(block, reg, ls2.val, new Imm(32), R3.R3Type.slli));
                                        list.add(new R3(block, reg, ls1.val, reg, R3.R3Type.adduw));
                                        list.add(new LS(block, reg, ls2.base, ls1.addr, LS.LSType.sd, AlignmentAnalysis.AlignType.ALIGN_BYTE_4));
                                    }
                                    needReplace.put(ls1, new ArrayList<>());
                                    needReplace.put(ls2, list);
                                } else if (ls1.type == LS.LSType.lw && ls2.type == LS.LSType.lw) {
                                    if (lsConflict(block, i, j, true, ls1.base, off)) break;
                                    ArrayList<RiscvInstruction> list1 = new ArrayList<>();
                                    ArrayList<RiscvInstruction> list2 = new ArrayList<>();
                                    //单独拿一个寄存器来存
                                    Reg reg = Reg.getVirtualReg(Reg.RegType.GPR, 64);
                                    list1.add(new LS(block, reg, ls1.base, ls1.addr, LS.LSType.ld, alignType));
                                    list1.add(new R2(block, ls1.val, reg, R2.R2Type.sextw));
                                    list2.add(new R3(block, ls2.val, reg, new Imm(32), R3.R3Type.srai));
                                    needReplace.put(ls1, list1);
                                    needReplace.put(ls2, list2);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (var key : needReplace.keySet()) {
            for (RiscvInstruction ri : needReplace.get(key)) {
                block.insertInstBefore(ri, key);
            }
            key.remove();
        }
    }
}
