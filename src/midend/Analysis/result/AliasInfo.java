package midend.Analysis.result;

import mir.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class AliasInfo {

    public final HashSet<Long> mDistinctPairs = new HashSet<>();
    public final ArrayList<HashSet<Integer>> mDistinctGroups = new ArrayList<>();
    public final HashMap<Value, ArrayList<Integer>> mPointerAttributes = new HashMap<>();
    public static final ArrayList<Integer> empty = new ArrayList<>();

    private static long code(int v1, int v2) {
        long code = 0;
        if (v1 < v2) {
            code += (long) v1 << 32;
            code += v2;
        } else {
            code += (long) v2 << 32;
            code += v1;
        }
        return code;
    }

    public void addPair(int v1, int v2) {
        if (v1 == v2) throw new RuntimeException("add same");
        mDistinctPairs.add(code(v1, v2));
    }

    public void addDistinctGroup(HashSet<Integer> set) {
        mDistinctGroups.add(new HashSet<>(set));
    }

    public void addValue(Value value, ArrayList<Integer> attrs) {
        if (!value.getType().isPointerTy()) throw new RuntimeException("wrong type");
        mPointerAttributes.put(value, new ArrayList<>(attrs));
    }

    public boolean isDistinct(Value v1, Value v2) {
        if (!mPointerAttributes.containsKey(v1) || !mPointerAttributes.containsKey(v2)) return false;
        if (v1 == v2) return false;
        var attr1 = mPointerAttributes.get(v1);
        var attr2 = mPointerAttributes.get(v2);
        for (int attrX : attr1) {
            for (int attrY : attr2) {
                if (attrX == attrY) continue;
                if (mDistinctPairs.contains(code(attrX, attrY))) return true;
                for (var group : mDistinctGroups) {
                    if (group.contains(attrX) && group.contains(attrY)) return true;
                }
            }
        }
        return false;
    }

    public boolean appendAttr(Value value, ArrayList<Integer> newAttrs) {
        if (newAttrs.isEmpty()) return false;
        if (!mPointerAttributes.containsKey(value)) mPointerAttributes.put(value, new ArrayList<>());
        var attrs = mPointerAttributes.get(value);
        int oldSize = attrs.size();
        attrs.addAll(newAttrs);
        HashSet<Integer> temp = new HashSet<>(attrs);
        attrs.clear();
        attrs.addAll(temp);
        Collections.sort(attrs);
        return oldSize != attrs.size();
    }

    public boolean appendAttr(Value value, int newAttr) {
        if (!mPointerAttributes.containsKey(value)) mPointerAttributes.put(value, new ArrayList<>());
        var attr = mPointerAttributes.get(value);
        if (!attr.contains(newAttr)) {
            attr.add(newAttr);
            return true;
        }
        return false;
    }

    public ArrayList<Integer> inheritFrom(Value ptr) {
        return mPointerAttributes.getOrDefault(ptr, empty);
    }
}
