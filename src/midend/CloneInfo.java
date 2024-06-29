package midend;

import mir.BasicBlock;
import mir.Loop;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;

public class CloneInfo {
    public static HashMap<Value, Value> valueMap = new HashMap<>();
//    public static HashMap<BasicBlock, BasicBlock> bbMap = new HashMap<>();
//    public static ArrayList<BasicBlock> CallbbCut = new ArrayList<>();

    public static void addValueReflect(Value src, Value tag) {
        valueMap.put(src, tag);
    }

    public static Value getReflectedValue(Value value) {
        if (valueMap.containsKey(value)) {
            return valueMap.get(value);
        }
        return value;
    }

    public static void clear() {
        valueMap.clear();
//        bbMap.clear();
    }

}
