package midend.Transform.DCE;

import midend.Analysis.AnalysisManager;
import midend.Analysis.I32RangeAnalysis;
import mir.Module;
import mir.*;

/**
 * 此过程执行“跳跃线程”，查看具有多个前任和多个后继的块。
 * <p>
 * 如果可以证明块的一个或多个前任总是跳转到后任之一，我们通过复制该块的内容将边从前任转发到后任。
 * <p>
 * 发生这种情况的一个例子是如下代码：
 * if () { ... X = 4; } if (X < 3) { ... }
 **/
public class JumpThread {

    private static I32RangeAnalysis irAnalyzer;

    public static void run(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            runOnFunc(function);
        }
    }

    public static void runOnFunc(Function function) {
        irAnalyzer = AnalysisManager.getI32Range(function);
        for (BasicBlock block : function.getBlocks()) {
            runOnBlock(block);
        }
    }

    public static void runOnBlock(BasicBlock block) {
        Instruction.Terminator term = block.getTerminator();
        if (term instanceof Instruction.Jump jump) {
            BasicBlock target = jump.getTargetBlock();
            if (!(target.getTerminator() instanceof Instruction.Branch)) return;
            Instruction.Branch br = (Instruction.Branch) target.getTerminator();
            if (!(br.getCond() instanceof Instruction.Icmp icmp)) return;
//            if (irAnalyzer)
        }
        else if (term instanceof Instruction.Branch br)

        {

        }
    }
}
