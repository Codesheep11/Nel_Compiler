package backend.Opt;

import backend.allocater.LivenessAnalyze;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;

import java.util.ArrayList;

import static backend.allocater.LivenessAnalyze.Out;

public class LivelessDCE {
    public static void run(RiscvModule module) {
        for (RiscvFunction function : module.funcList) {
            if (function.isExternal) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(RiscvFunction function) {
        LivenessAnalyze.RunOnFunc(function);
        ArrayList<RiscvInstruction> delList = new ArrayList<>();
        for (RiscvBlock block : function.blocks) {
            for (RiscvInstruction inst : block.riscvInstructions) {
                if (canbeDelete(inst)) delList.add(inst);
            }
        }
        if (!delList.isEmpty()) {
            System.out.println("delete");
            for (RiscvInstruction inst : delList) {
                System.out.println(inst);
            }
        }
//        delList.forEach(inst -> inst.delete());
    }

    private static boolean canbeDelete(RiscvInstruction inst) {
        if (inst instanceof J) return false;
        if (inst.def.isEmpty()) return false;
        return !Out.get(inst).contains(inst.def.iterator().next());
    }
}
