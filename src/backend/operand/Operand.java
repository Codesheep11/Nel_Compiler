package backend.operand;

import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.HashSet;

public abstract class Operand {
    // 使用该变量的指令: 指令构造时维护
    public HashSet<RiscvInstruction> use = new HashSet<>();

}
