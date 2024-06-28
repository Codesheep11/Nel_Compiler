package midend;

import mir.*;
import mir.Module;

import java.util.HashSet;
import java.util.LinkedHashMap;

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

    /**
     *  just for test <br>
     *  请勿将该函数作为优化 API 调用
     *
     * @param module 模块
     */
    public static void test(Module module) {
        for (Function function : module.getFuncSet()) {
            if (function.isExternal()) continue;
            for(Loop loop : function.rootLoop.children)
                run(loop);
        }
    }

    public static void run(Loop loop) {
        for (Loop child : loop.children) {
            run(child);
        }
        simplifyPreHeader(loop);
        simplifyLatch(loop);
        CanonicalizeExits(loop);
    }

    private static void simplifyPreHeader(Loop loop) {
        if (loop.enterings.size() <= 1)
            return;
        // TODO: 可能需要修改 新建块的loop归属
        Loop loop1 = loop.enterings.iterator().next().loop;
        Function parentFunction = loop.header.getParentFunction();
        BasicBlock newPreHeader = BasicBlock.getNewCleanBlock(getNewLabel(parentFunction, "preHeader"), parentFunction, loop1);
        for (BasicBlock entering : loop.enterings) {
            entering.replaceSucc(loop.header, newPreHeader);
        }
        // 将需要维护 phi 信息提前
        for (Instruction.Phi phi : loop.header.getPhiInstructions()){
            LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
            for (BasicBlock entering : loop.enterings) {
                newMap.put(entering, phi.getOptionalValue(entering));
                phi.removeOptionalValue(entering);
            }
            Instruction.Phi val = new Instruction.Phi(newPreHeader, phi.getType(), newMap);
            phi.addOptionalValue(newPreHeader, val);
        }
        new Instruction.Jump(newPreHeader, loop.header);

        parentFunction.getBlocks().insertBefore(newPreHeader, loop.header);

        loop.enterings.clear();
        loop.enterings.add(newPreHeader);
    }

    private static void simplifyLatch(Loop loop) {
        if (loop.latchs.size() <= 1)
            return;
        Function parentFunction = loop.header.getParentFunction();
        BasicBlock newLatch = BasicBlock.getNewCleanBlock(getNewLabel(parentFunction, "latch"), parentFunction, loop);
        // 更新所有 latch 的后继 跳转指令
        for (BasicBlock latch : loop.latchs) {
            latch.replaceSucc(loop.header, newLatch);
        }
        // 将需要维护 phi 信息提前
        for (Instruction.Phi phi : loop.header.getPhiInstructions()){
            LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
            for (BasicBlock latch : loop.latchs) {
                newMap.put(latch, phi.getOptionalValue(latch));
                phi.removeOptionalValue(latch);
            }
            Instruction.Phi val = new Instruction.Phi(newLatch, phi.getType(), newMap);
            phi.addOptionalValue(newLatch, val);
        }
        // 在末尾插入跳转指令
        new Instruction.Jump(newLatch, loop.header);

        // 启发式插入
        parentFunction.getBlocks().insertAfter(newLatch, loop.latchs.iterator().next());

        loop.latchs.clear();
        loop.latchs.add(newLatch);
    }

    private static void CanonicalizeExits(Loop loop) {
        HashSet<BasicBlock> newExits = new HashSet<>();
        Function parentFunction = loop.header.getParentFunction();
        for (BasicBlock exit : loop.exits) {
            if (!exit.getDomSet().contains(loop.header)) {
                // TODO: 可能需要修改 新建块的loop归属
                Loop loop1 = exit.loop;
                BasicBlock newExit = BasicBlock.getNewCleanBlock(getNewLabel(loop.header.getParentFunction(), "exit"), loop.header.getParentFunction(), loop1);
                loop.exitings.forEach(exiting -> exiting.replaceSucc(exit, newExit));
                // 将需要维护 phi 信息提前
                for (Instruction.Phi phi : exit.getPhiInstructions()){
                    LinkedHashMap<BasicBlock, Value> newMap = new LinkedHashMap<>();
                    for (BasicBlock exiting : loop.exitings) {
                        if (phi.containsBlock(exiting)) {
                            newMap.put(exiting, phi.getOptionalValue(exiting));
                            phi.removeOptionalValue(exiting);
                        }
                    }
                    Instruction.Phi val = new Instruction.Phi(newExit, phi.getType(), newMap);
                    phi.addOptionalValue(newExit, val);
                }

                parentFunction.getBlocks().insertBefore(newExit, exit);

                new Instruction.Jump(newExit, exit);
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
