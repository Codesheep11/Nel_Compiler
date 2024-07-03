package backend.Opt.Scheduler;

import backend.riscv.RiscvBlock;
import backend.riscv.RiscvFunction;

public class ScheduleModel {
    public static boolean enablePostRAScheduling = true;
    public static boolean hasRegRenaming = false;
    public static boolean hasMacroFusion = false;
    public static int issueWidth = 2;// 流水线宽度
    public static boolean outOfOrder = false;
    public static boolean hardwarePrefetch = true;
    public static int maxDataStreams = 8;
    public static int maxStrideByBytes = 256;
    private static final boolean preRA = true;
    private static final boolean postSA = true;
    private static final boolean inSSAForm = true;

    public boolean peepholeOpt(RiscvFunction func) {
        boolean modified = false;
        if (preRA) {
            for (RiscvBlock block : func.blocks) {
                modified |= earlyFoldStore(block);
                modified |= earlyFoldLoad(block);
            }
        }
        if (postSA) {
            for (RiscvBlock block : func.blocks) {
                modified |= foldStoreZero(func, block);
            }
        }
        modified |= branch2jump(func);
        modified |= removeDeadBranch(func);
        modified |= simplifyOpWithZero(func);
        modified |= relaxWInst(func);
        modified |= removeSExtW(func);
        modified |= expandMulWithConstant(func, queryTuneOpt("max_mul_constant_cost", 2));
        if (inSSAForm) {
            modified |= earlyFoldDoubleWordCopy(func);
        }
        return modified;
    }


    private boolean earlyFoldStore(RiscvBlock block) {
        // Implementation here
        return false;
    }

    private boolean earlyFoldLoad(RiscvBlock block) {
        // Implementation here
        return false;
    }

    private boolean foldStoreZero(RiscvFunction func, RiscvBlock block) {
        // Implementation here
        return false;
    }

    private boolean branch2jump(RiscvFunction func) {
        // Implementation here
        return false;
    }

    private boolean removeDeadBranch(RiscvFunction func) {
        // Implementation here
        return false;
    }

    private boolean simplifyOpWithZero(RiscvFunction func) {
        // Implementation here
        return false;
    }

    private boolean relaxWInst(RiscvFunction func) {
        // Implementation here
        return false;
    }

    private boolean removeSExtW(RiscvFunction func) {
        // Implementation here
        return false;
    }

    private boolean expandMulWithConstant(RiscvFunction func, int maxMulConstantCost) {
        // Implementation here
        return false;
    }

    private boolean earlyFoldDoubleWordCopy(RiscvFunction func) {
        // Implementation here
        return false;
    }

    private int queryTuneOpt(String key, int defaultValue) {
        // Implementation here
        return 0;
    }
}
