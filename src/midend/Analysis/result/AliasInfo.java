package midend.Analysis.result;

import mir.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public class AliasInfo {

    public HashSet<Long> mDistinctPairs;
    public ArrayList<HashSet<Value>> mDistinctGroups;
    public HashMap<Value, ArrayList<Value>> mPointerAttributes;
    public static final ArrayList<Value> empty = new ArrayList<>();

    private static long code(Value v1, Value v2) {
        long code = 0;
        if (v1.hashCode() < v2.hashCode()) {
            code += (long) v1.hashCode() << 32;
            code += v2.hashCode();
        } else {
            code += (long) v2.hashCode() << 32;
            code += v1.hashCode();
        }
        return code;
    }

    public void addPair(Value v1, Value v2) {
        if (v1.equals(v2)) throw new RuntimeException("add same");
        mDistinctPairs.add(code(v1, v2));
    }

    public void addDistinctGroup(HashSet<Value> set) {
        mDistinctGroups.add(new HashSet<>(set));
    }

    public void addValue(Value value, ArrayList<Value> attrs) {
        if (!value.getType().isPointerTy()) throw new RuntimeException("wrong type");
        mPointerAttributes.put(value, new ArrayList<>(attrs));
    }

    public boolean isDistinct(Value v1, Value v2) {
        if (!mPointerAttributes.containsKey(v1) || !mPointerAttributes.containsKey(v2)) return false;
        if (v1 == v2) return false;
        var attr1 = mPointerAttributes.get(v1);
        var attr2 = mPointerAttributes.get(v2);
        for (var attrX : attr1) {
            for (var attrY : attr2) {
                if (attrX == attrY) continue;
                if (mDistinctPairs.contains(code(v1, v2))) return true;
                for (var group : mDistinctGroups) {
                    if (group.contains(attrX) && group.contains(attrY)) return true;
                }
            }
        }
        return false;
    }

    public boolean appendAttr(Value value, ArrayList<Value> newAttrs) {
        if (newAttrs.isEmpty()) return false;
        if (!mPointerAttributes.containsKey(value)) throw new RuntimeException("wrong!");
        var attrs = mPointerAttributes.get(value);
        int oldSize = attrs.size();
        attrs.addAll(newAttrs);
        HashSet<Value> temp = new HashSet<>(attrs);
        attrs.clear();
        attrs.addAll(temp);
        attrs.sort(Comparator.comparingInt(Value::hashCode));
        return oldSize != attrs.size();
    }

    public boolean appendAttr(Value value, Value newAttr) {
        if (!mPointerAttributes.containsKey(value)) throw new RuntimeException("wrong!");
        var attr = mPointerAttributes.get(value);
        if (!attr.contains(newAttr)) {
            attr.add(newAttr);
            return true;
        }
        return false;
    }

    public ArrayList<Value> inheritFrom(Value ptr) {

    }
}
