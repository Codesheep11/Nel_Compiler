package mir.result;

import mir.SCEVExpr;
import mir.Value;

import java.util.HashMap;

public class SCEVAnalysisResult {

    private HashMap<Value, SCEVExpr> map = new HashMap<>();

    public void addSCEV(Value value, SCEVExpr cr) {
        map.put(value, cr);
    }

    public SCEVExpr query(Value value) {
        return map.get(value);
    }
}
