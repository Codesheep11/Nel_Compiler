package midend;

import mir.*;
import mir.Module;

import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * 循环反转
 * <p>
 * 用于辅助 LoopUnroll 的工作 <br>
 * 在规范化规约变量之后进行indvars 效果最佳 <br>
 *
 * TODO: 不是所有的循环都适合循环反转+展开，需要增设评估函数
 * @author Srchycz
 */
public class LoopRotate {

    private static int count = 0;


//    public static void run(Loop loop) {
//        for (Loop child : loop.children) {
//            run(child);
//        }
//        setLoopGuard(loop);
//        simplifyLatch(loop);
//        CanonicalizeExits(loop);
//    }

//    private static void setLoopGuard(Loop loop) {
//        if (loop.cond instanceof Constant) {
//            return;
//        }
//        if (loop.cond instanceof Instruction inst) {
//            BasicBlock guard = new BasicBlock(getNewLabel(loop.header.getParentFunction(), "guard"), loop.header.getParentFunction());
//            inst.cloneToBB(guard);
//            new Instruction.Branch(guard, inst, loop.header, loop.getExit());
//            loop.enterings.forEach(entering -> entering.replaceSucc(loop.header, guard));
//            loop.enterings.clear();
//            loop.enterings.add(guard);
//        }
//    }

    private static String getNewLabel(Function function, String infix) {
        return function.getName() + infix + count++;
    }
}
