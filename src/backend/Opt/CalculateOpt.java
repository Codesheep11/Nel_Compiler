package backend.Opt;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import mir.Ir2RiscV.VirRegMap;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CalculateOpt {
    public static void run(RiscvModule riscvModule) {
        for (RiscvFunction function : riscvModule.funcList) {
            liAdd2Addi(function);
            for (RiscvBlock block : function.blocks) {
                ConstValueReUse(block);
                ConstPointerReUse(block);
                icmpBranchToBranch(block);
            }
        }
    }

    //
    public static void liAdd2Addi(RiscvFunction function) {
        for (RiscvBlock block : function.blocks) {
            ArrayList<RiscvInstruction> newList = new ArrayList<>();
            for (int i = 0; i < block.riscvInstructions.getSize(); i++) {
                RiscvInstruction now = block.riscvInstructions.get(i);
                if (i != block.riscvInstructions.getSize() - 1) {
                    RiscvInstruction next = block.riscvInstructions.get(i + 1);
                    if (now instanceof Li && next instanceof R3) {
                        Reg liReg = ((Li) now).reg;
                        Reg opRs1 = (Reg) ((R3) next).rs1;
                        Reg opRs2 = (Reg) ((R3) next).rs2;
                        R3 opi = null;
                        if (((R3) next).type == R3.R3Type.addw) {
                            if (((Li) now).imm >= -2047 && ((Li) now).imm <= 2047) {
                                // 如果li的寄存器和addw用的寄存器之一重合
                                if (liReg.equals(opRs1)) {
                                    opi = new R3(block, ((R3) next).rd, opRs2, new Imm(((Li) now).imm), R3.R3Type.addi);
                                } else if (liReg.equals(opRs2)) {
                                    opi = new R3(block, ((R3) next).rd, opRs1, new Imm(((Li) now).imm), R3.R3Type.addi);
                                }
                                if (opi != null) {
                                    newList.add(opi);
                                    i++;// 跳过next
                                    continue;
                                }
                            }
                        } else if (((R3) next).type == R3.R3Type.subw) {
                            if (((Li) now).imm >= -2047 && ((Li) now).imm <= 2047) {
                                // 如果li的寄存器和sub的减的寄存器重合，因为如果被减的话需要反转另一个参数
                                if (liReg.equals(opRs2)) {
                                    opi = new R3(block, ((R3) next).rd, opRs1, new Imm(((Li) now).imm * -1), R3.R3Type.addi);
                                    newList.add(opi);
                                    i++;// 跳过next
                                    continue;
                                }
                            }
                        }
                    }
                }
                newList.add(now);
            }
            block.riscvInstructions.setEmpty();
            for (RiscvInstruction instr : newList) {
                block.riscvInstructions.addLast(instr);
            }
        }
    }


    // 重要依据，这些拉取的常数和全局的不会跨过块
    public static void ConstValueReUse(RiscvBlock riscvBlock) {
        final int range = 10;
        HashMap<Integer, Pair<Reg, Integer>> map = new HashMap<>();
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < riscvBlock.riscvInstructions.getSize(); i++) {
            RiscvInstruction instr = riscvBlock.riscvInstructions.get(i);
            for (int idx = 0; idx < instr.getOperandNum(); idx++) {
                if (instr.isDef(idx) && !(instr instanceof Li)) {
                    int finalIdx = idx;
                    map.keySet().removeIf(key -> map.get(key).first.equals(instr.getRegByIdx(finalIdx)));
                }//删除所有重定义的
            }
            if (instr instanceof Li) {
                // 如果前面有记录这个值，那么就将这个li给删掉，然后将后面的所有使用这个的寄存器都换掉
                if (map.containsKey(((Li) instr).imm)) {
                    Reg now = map.get(((Li) instr).imm).first;
                    Reg def = ((Li) instr).reg;
                    for (int j = i + 1; j < riscvBlock.riscvInstructions.getSize(); j++) {
                        RiscvInstruction needReplace = riscvBlock.riscvInstructions.get(j);
                        if (needReplace instanceof J) continue;
                        needReplace.replaceUseReg(def, now);
                    }
                    map.get(((Li) instr).imm).second = range;// 刷新生存周期
                    continue;
                } else {
                    map.keySet().removeIf(key -> map.get(key).first.equals(((Li) instr).reg));
                    map.put(((Li) instr).imm, new Pair<>(((Li) instr).reg, range));
                }
            }
            newList.add(instr);
            HashSet<Integer> needRemove = new HashSet<>();
            for (Integer key : map.keySet()) {
                map.get(key).second--;
                if (map.get(key).second == 0) {
                    needRemove.add(key);
                }
            }
            for (Integer need : needRemove) {
                map.remove(need);
            }
        }
        riscvBlock.riscvInstructions.setEmpty();
        for (RiscvInstruction ri : newList) {
            riscvBlock.riscvInstructions.addLast(ri);
        }
    }

    public static void Mul2SrAdd(RiscvBlock block) {

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
    public static void icmpBranchToBranch(RiscvBlock block) {
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < block.riscvInstructions.getSize(); i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            boolean modified = false;
            if (i < block.riscvInstructions.getSize() - 1) {
                RiscvInstruction next = block.riscvInstructions.get(i + 1);
                if (matchSLTAndSGT(now, next)) {
                    //slt ,置1，bne r,zero,不为0则跳转,所以就是小于则跳转
                    Reg reg = (Reg) ((R3) now).rd;
                    if (VirRegMap.bUseReg.get(reg) <= 1) {
                        newList.add(new B(block, B.BType.blt, ((R3) now).rs1, (((R3) now).rs2), ((B) next).targetBlock));
                        i++;
                        // 将next忽略
                        modified = true;
                    }
                }
                else if (i < block.riscvInstructions.getSize() - 2) {
                    RiscvInstruction farNext = block.riscvInstructions.get(i + 2);
                    if (matchFarNext(now, next, farNext)) {
                        B.BType type;
                        if (matchEQ(now, next, farNext)) {
                            // subw,seqz,看看是不是0
                            type = B.BType.beq;
                        } else if (matchNE(now, next, farNext)) {
                            type = B.BType.bne;
                        } else if (matchSGEAndSLE(now, next, farNext)) {
                            // 这个存起来就看后面的是不是大于等于前面的
                            type = B.BType.bge;
                        } else throw new RuntimeException("wrong match");
                        assert next instanceof R2;
                        Reg reg = (Reg) ((R2) next).rd;
                        if (VirRegMap.bUseReg.get(reg) <= 1) {
                            newList.add(new B(block, type, ((R3) now).rs1, ((R3) now).rs2, ((B) farNext).targetBlock));
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
        newList.forEach(System.out::println);
        block.riscvInstructions.setEmpty();
        for (RiscvInstruction ri : newList) {
            block.riscvInstructions.addLast(ri);
        }
    }

    public static void ConstPointerReUse(RiscvBlock riscvBlock) {
        final int range = 10;
        HashMap<RiscvGlobalVar, Pair<Reg, Integer>> map = new HashMap<>();
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < riscvBlock.riscvInstructions.getSize(); i++) {
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
                    for (int j = i + 1; j < riscvBlock.riscvInstructions.getSize(); j++) {
                        RiscvInstruction needReplace = riscvBlock.riscvInstructions.get(j);
                        if (needReplace instanceof J) continue;
                        needReplace.replaceUseReg(def, now);
                    }
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
        riscvBlock.riscvInstructions.setEmpty();
        for (RiscvInstruction ri : newList) {
            riscvBlock.riscvInstructions.addLast(ri);
        }
    }
}
