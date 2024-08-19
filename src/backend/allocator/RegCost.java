package backend.allocator;

import backend.operand.Reg;
import backend.riscv.RiscvInstruction.LS;
import backend.riscv.RiscvInstruction.RiscvInstruction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public class RegCost {
    public static final HashMap<Reg, Integer> RegCostMap = new HashMap<>();

    public static void buildSpillCost(HashSet<Reg> regs) {
        RegCostMap.clear();
        for (Reg reg : regs) CalRegCost(reg);
    }

    public static ArrayList<Reg> getSpillArray() {
        ArrayList<Reg> regs = new ArrayList<>(RegCostMap.keySet());
        regs.sort(Comparator.comparingInt(RegCostMap::get));
        if (regs.size() > 20) regs = new ArrayList<>(regs.subList(0, regs.size() / 2));
        return regs;
    }

    private static void CalRegCost(Reg reg) {
        int cost = 0;
        for (RiscvInstruction ri : LivenessAnalyze.RegUse.get(reg)) {
            int loopDepth = ri.block.loopDepth + 1;
            if (ri instanceof LS ls) {
                if (ls.isSpilled && ls.val.equals(reg)) cost += 5 * 2000 * loopDepth;
                else cost += 5 * 100 * loopDepth;
            }
            else cost += 1 * 100 * loopDepth;
        }
        if (reg.preColored) cost += 50;
//        if (reg.temp) cost /= 20;
        RegCostMap.put(reg, cost);
    }
}
