package midend;

import manager.CentralControl;
import mir.BasicBlock;
import mir.Constant;
import mir.Instruction;
import mir.Loop;

import java.util.ArrayList;

/**
 * 循环分支取消
 * <p>
 * 请在执行该优化前先执行 LIVL 优化
 */
public class LoopUnSwitch {

    public static void run() {
        if (!CentralControl._LUS_OPEN) return;

    }

    private void unSwitch(Loop loop) {
        ArrayList<Instruction.Branch> branches = new ArrayList<>();
        for (BasicBlock block : loop.nowLevelBB) {
            if (block.getLastInst() instanceof Instruction.Branch branch) {
                // 常量条件 - 可被其他优化处理
                if (branch.getCond() instanceof Constant)
                    continue;
                // 循环内依赖条件 不能提出循环
                if (loop.defValue(branch.getCond()))
                    continue;
                branches.add(branch);
            }
        }
        if (branches.isEmpty()) return;
    }
}
