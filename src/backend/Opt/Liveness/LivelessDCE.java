package backend.Opt.Liveness;

import backend.allocator.LivenessAnalyze;
import backend.operand.Reg;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.AMOadd;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;
import utils.NelLinkedList;

import java.util.ArrayList;

import static backend.allocator.LivenessAnalyze.*;

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
            // System.err.println(delList);
            delList.forEach(NelLinkedList.NelLinkNode::remove);
            LivenessAnalyze.RunOnFunc(function);
        }
    }

    private static boolean canbeDelete(RiscvInstruction inst) {
        if (inst instanceof J) return false;
        if (inst instanceof AMOadd) return false;
        if (Def.get(inst).isEmpty()) return false;
        Reg def = Def.get(inst).iterator().next();
        if (RegUse.get(def).size() == 1) return true;
        return !Out.get(inst).contains(Def.get(inst).iterator().next());
    }
}
