package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instruction;
import mir.Loop;

import java.util.HashSet;

/**
 * 循环简化
 * <p>
 * 事实上该类不是优化类, 它用于辅助其他优化类的工作 <br>
 * PostCondition: <br>
 * 1. 循环的preHeader唯一 <br>
 * 2. 循环的latch唯一 <br>
 * 3. 所有 exit 块被 header支配 <br>
 *
 * @author Sychycz
 */
public class LoopSimplifyForm {

    private static int count = 0;

    public static void run(Loop loop) {
        simplifyPreHeader(loop);
        simplifyLatch(loop);
        CanonicalizeExits(loop);
    }

    private static void simplifyPreHeader(Loop loop) {
        if (loop.exitings.size() <= 1)
            return;
        // TODO: 可能需要修改 新建块的loop归属
        Function parentFunction = loop.header.getParentFunction();
        BasicBlock newPreHeader = BasicBlock.getNewCleanBlock(getNewLabel(parentFunction, "preHeader"), parentFunction, loop);
        for (BasicBlock entering : loop.enterings) {
            entering.replaceSucc(loop.header, newPreHeader);
            newPreHeader.addPreBlock(entering);
            loop.header.replacePred(entering, newPreHeader);
        }
        newPreHeader.addSucBlock(loop.header);
        parentFunction.getBlocks().insertBefore(newPreHeader, loop.header);

        newPreHeader.addInstLast(new Instruction.Jump(newPreHeader, loop.header));

        loop.enterings.clear();
        loop.enterings.add(newPreHeader);
    }

    private static void simplifyLatch(Loop loop) {
        if (loop.latchs.size() <= 1)
            return;
        Function parentFunction = loop.header.getParentFunction();
        BasicBlock newLatch = BasicBlock.getNewCleanBlock(getNewLabel(parentFunction, "latch"), parentFunction, loop);
        for (BasicBlock latch : loop.latchs) {
            latch.replaceSucc(loop.header, newLatch);
            newLatch.addPreBlock(latch);
            loop.header.replacePred(latch, newLatch);
        }
        newLatch.addSucBlock(loop.header);
        // 启发式插入
        parentFunction.getBlocks().insertAfter(newLatch, loop.latchs.iterator().next());

        newLatch.addInstLast(new Instruction.Jump(newLatch, loop.header));

        loop.latchs.clear();
        loop.latchs.add(newLatch);
    }

    private static void CanonicalizeExits(Loop loop) {
        HashSet<BasicBlock> newExits = new HashSet<>();
        for (BasicBlock exit : loop.exits) {
            if (!exit.getDomSet().contains(loop.header)) {
                BasicBlock newExit = BasicBlock.getNewCleanBlock(getNewLabel(loop.header.getParentFunction(), "exit"), loop.header.getParentFunction(), loop);
                loop.exitings.forEach(exiting -> exiting.replaceSucc(exit, newExit));
                loop.header.replacePred(exit, newExit);

                newExit.addInstLast(new Instruction.Jump(newExit, exit));
                newExits.add(newExit);
            } else {
                newExits.add(exit);
            }
        }
        loop.exits = newExits;
    }

    private static String getNewLabel(Function function, String infix) {
        return function.getName() + infix + count++;
    }
}
