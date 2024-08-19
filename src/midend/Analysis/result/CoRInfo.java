package midend.Analysis.result;

import mir.CoR;
import mir.Loop;
import mir.Value;

import java.util.HashMap;

public class CoRInfo {
    private final HashMap<Value, CoR> map = new HashMap<>();

    public void addCoR(Value value, CoR cr) {
        map.put(value, cr);
    }

    public CoR query(Value value, Loop loop) {
        if (!map.containsKey(value)) {
            if (!loop.defValue(value)) {
                return new CoR(CoR.CoRType.Value, value);
//                addCoR(value, new CoR(CoR.CoRType.Value, value));
            }
        }
        return map.get(value);
    }

    public boolean contains(Value value) {
        if (!map.containsKey(value))
            return false;
        return map.get(value) != null;
    }
}
