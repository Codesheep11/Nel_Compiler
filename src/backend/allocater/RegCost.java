package backend.allocater;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class RegCost {
    public static HashMap<Reg, Integer> RegCostMap = new HashMap<>();

    public static void buildSpillCost(HashSet<Reg> regs) {
        RegCostMap.clear();
        for (Reg reg : regs) CalRegCost(reg);
    }

    public static ArrayList<Reg> getSpillArray() {
        ArrayList<Reg> regs = new ArrayList<>(RegCostMap.keySet());
        regs.sort((r1, r2) -> RegCostMap.get(r1) - RegCostMap.get(r2));
        if (regs.size() > 20) regs = new ArrayList<>(regs.subList(0, 2 * regs.size() / 3));
        return regs;
    }

    private static void CalRegCost(Reg reg) {
        int cost = 0;
        for (RiscvInstruction ri : LivenessAnalyze.RegUse.get(reg)) {
            int loopDepth = ri.block.loopDepth + 1;
            if (ri instanceof LS ls) {
                if (ls.isSpilled) return;
                else cost += 5 * 100 * loopDepth;
            }
            else cost += 1 * 100 * loopDepth;
        }
        if (reg.preColored) cost += 50;
        RegCostMap.put(reg, cost);
    }
}
