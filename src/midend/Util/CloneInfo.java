package midend.Util;

import mir.Value;

import java.util.HashMap;

public class CloneInfo {
    public final HashMap<Value, Value> valueMap;

    public CloneInfo() {
        valueMap = new HashMap<>();
    }

    public void addValueReflect(Value src, Value tag) {
        valueMap.put(src, tag);
    }

    public Value getReflectedValue(Value value) {
        if (valueMap.containsKey(value)) {
            return valueMap.get(value);
        }
        return value;
    }

    public void clear() {
        valueMap.clear();
    }

    public boolean containValue(Value value) {
        return valueMap.containsKey(value);
    }

    public void merge(CloneInfo cloneInfo) {
        for (Value key : cloneInfo.valueMap.keySet()) {
            if (valueMap.containsKey(key)) {
                throw new RuntimeException("CloneInfo merge error");
            }
        }
        valueMap.putAll(cloneInfo.valueMap);
    }

}
