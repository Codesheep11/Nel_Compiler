package backend.Opt;

import backend.allocater.LivenessAnalyze;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import mir.Ir2RiscV.VirRegMap;
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
                PreRAConstPointerReUse(block);
            }
        }
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                icmpBranchToBranch(block);
            }
        }
    }

    // 重要依据，这些拉取的常数和全局的不会跨过块

    private static void uselessLoadRemove(RiscvBlock block) {
        HashSet<RiscvInstruction> needRemove = new HashSet<>();
        for (RiscvInstruction instr : block.riscvInstructions) {
            if (instr == block.riscvInstructions.getFirst()) continue;
            RiscvInstruction before = (RiscvInstruction) instr.prev;
            if (before instanceof LS be && instr instanceof LS af) {
                if (be.rs1.equals(af.rs1) && be.rs2.equals(af.rs2) && be.addr.equals(af.addr)) {
                    needRemove.add(instr);
                }
            }
        }
        for (RiscvInstruction ri : needRemove) {
            ri.remove();
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
            block.riscvInstructions.addLast(ri);
        }
    }

    private static void PreRAConstValueReUse(RiscvBlock riscvBlock) {
        final int range = 10;
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
                    now.mergeReg(def);
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
            riscvBlock.riscvInstructions.addLast(ri);
        }
    }

    private static void PreRAConstPointerReUse(RiscvBlock riscvBlock) {
        final int range = 10;
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
                    now.mergeReg(def);
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
            riscvBlock.riscvInstructions.addLast(ri);
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
            }
        }
    }

    private static void removeSameMv(RiscvBlock riscvBlock) {
        Iterator<RiscvInstruction> iterator = riscvBlock.riscvInstructions.iterator();
        while (iterator.hasNext()) {
            RiscvInstruction ri = iterator.next();
            if (ri instanceof R2 r2 && r2.type == R2.R2Type.mv) {
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
            block.riscvInstructions.insertBefore(pair.second, pair.first);
            pair.first.remove();
        }
    }
}
