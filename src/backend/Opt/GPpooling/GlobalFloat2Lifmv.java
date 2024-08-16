package backend.Opt.GPpooling;

import backend.operand.Imm;
import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFloat;
import backend.riscv.RiscvFunction;
import backend.riscv.RiscvInstruction.*;
import backend.riscv.RiscvModule;
import utils.Pair;

import java.util.HashMap;

public class GlobalFloat2Lifmv {
    public static void run(RiscvModule riscvModule) {
        riscvModule.globList.removeIf(rb -> rb instanceof RiscvFloat rf);
        // 对于所有使用这里面值的指令,将la+lw转换成lw偏移
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal) continue;
            for (RiscvBlock block : function.blocks) {
                runOnBlock(block);
            }
        }
    }

    private static void runOnBlock(RiscvBlock block) {
        HashMap<Pair<La, LS>, Pair<Li, R2>> needReplace = new HashMap<>();
        for (int i = 0; i < block.riscvInstructions.size() - 1; i++) {
            RiscvInstruction now = block.riscvInstructions.get(i);
            RiscvInstruction next = block.riscvInstructions.get(i + 1);
            if (now instanceof La la && next instanceof LS ls) {
                if (la.content instanceof RiscvFloat ri) {
                    if (la.reg.equals(ls.base)) {
                        // 代表可以换掉
                        needReplace.put(
                                new Pair<>(la, ls),
                                new Pair<>(new Li(block, la.reg, new Imm(ri.getData())),
                                        new R2(block, ls.val, la.reg, R2.R2Type.fmvwx))
                        );
                    }
                }
            }
        }
        for (var pair : needReplace.keySet()) {
            var _pair = needReplace.get(pair);
            block.insertInstBefore(_pair.first, pair.first);
            block.insertInstBefore(_pair.second, pair.first);
            pair.first.remove();
            pair.second.remove();
        }
    }
}
