package mir.result;

import mir.Constant;
import mir.SCEVExpr;
import mir.Value;

import java.util.HashMap;

public class SCEVInfo {

    private HashMap<Value, SCEVExpr> map = new HashMap<>();

    public void addSCEV(Value value, SCEVExpr cr) {
        map.put(value, cr);
    }

    public SCEVExpr query(Value value) {
        if (!map.containsKey(value)) {
            if (value instanceof Constant.ConstantInt constantInt) {
                addSCEV(value, new SCEVExpr(SCEVExpr.SCEVType.Constant, constantInt.getIntValue()));
            }
        }
        return map.get(value);
    }

    public boolean contains(Value value) {
        return map.containsKey(value);
    }
}
