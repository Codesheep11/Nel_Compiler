package midend;

import mir.BasicBlock;
import mir.Loop;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;

public class CloneInfo {
    public static HashMap<Value, Value> valueMap = new HashMap<>();
    public static HashMap<Loop, Loop> loopMap = new HashMap<>();
    public static HashMap<BasicBlock, BasicBlock> bbMap = new HashMap<>();
    public static ArrayList<BasicBlock> CallbbCut = new ArrayList<>();

    public static void addValueReflect(Value src, Value tag) {
        valueMap.put(src, tag);
    }

    public static Value getReflectedValue(Value value) {
        if (valueMap.containsKey(value)) {
            return valueMap.get(value);
        }
        return value;
    }

    public static void addLoopReflect(Loop src, Loop tag) {
        loopMap.put(src, tag);
    }

    public static void clear() {
        valueMap.clear();
        loopMap.clear();
        bbMap.clear();
    }

    public static void fixLoopReflect() {
        if (!CallbbCut.get(0).loop.isRoot) {
            Loop ori = CallbbCut.get(0).loop;
            BasicBlock tmp = CallbbCut.get(0);
            if (ori.latchs.contains(tmp)) {
                ori.latchs.remove(tmp);
                ori.latchs.add(CallbbCut.get(1));
            }
        }
        for (Loop loop : loopMap.keySet()) {
            loop.cloneFix(loopMap.get(loop));
        }
    }
}
