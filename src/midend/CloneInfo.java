package midend;

import mir.BasicBlock;
import mir.Loop;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;

public class CloneInfo {
    public HashMap<Value, Value> valueMap;

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

}
