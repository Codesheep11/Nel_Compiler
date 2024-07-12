package backend.Opt;

import backend.allocater.LivenessAnalyze;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.J;
import backend.riscv.RiscvInstruction.RiscvInstruction;
import backend.riscv.RiscvModule;
import utils.SyncLinkedList;

import java.util.ArrayList;

import static backend.allocater.LivenessAnalyze.Def;
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
            System.err.println(delList);
            delList.forEach(SyncLinkedList.SyncLinkNode::remove);
            LivenessAnalyze.RunOnFunc(function);
        }
    }

    private static boolean canbeDelete(RiscvInstruction inst) {
        if (inst instanceof J) return false;
        if (Def.get(inst).isEmpty()) return false;
        return !Out.get(inst).contains(Def.get(inst).iterator().next());
    }
}
