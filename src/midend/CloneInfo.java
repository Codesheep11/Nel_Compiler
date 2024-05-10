package midend;
import mir.Value;

import java.util.HashMap;

public class CloneInfo {
    public static HashMap<Integer, Integer> loopCondCntMap = new HashMap<>();
    public static HashMap<Value, Value> valueMap = new HashMap<>();

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
        loopCondCntMap.clear();
        valueMap.clear();
        //loopNeedFix.clear();
    }

}
