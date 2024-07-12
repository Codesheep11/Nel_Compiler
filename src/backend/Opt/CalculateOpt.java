package backend.Opt;

import backend.operand.Imm;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvGlobalVar;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
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

    // 将icmp和branch合并
    public static void icmpBranchToBranch(RiscvBlock block) {
        ArrayList<RiscvInstruction> newList = new ArrayList<>();
        for (int i = 0; i < block.riscvInstructions.getSize(); i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            if (i == block.riscvInstructions.getSize() - 2) {
                newList.add(now);
                break;
            }
            RiscvInstruction next = block.riscvInstructions.get(i + 1);
            RiscvInstruction farNext = block.riscvInstructions.get(i + 2);

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
