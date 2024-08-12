//package midend.Analysis.result;
//
//import mir.Value;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//
//public class AliasInfo {
//
//    public HashSet<Long> mDistinctPairs;
//    public ArrayList<HashSet<Value>> mDistinctGroups;
//    public HashMap<Value, ArrayList<Value>> mPointerAttributes;
//    public static final ArrayList<Value> empty=new ArrayList<>();
//
//    public void addPair(Value v1, Value v2) {
//        if (v1.equals(v2)) throw new RuntimeException("add same");
//        long code = 0;
//        if (v1.hashCode() < v2.hashCode()) {
//            code += (long) v1.hashCode() << 32;
//            code += v2.hashCode();
//            mDistinctPairs.add(code);
//        } else {
//            code += (long) v2.hashCode() << 32;
//            code += v1.hashCode();
//            mDistinctPairs.add(code);
//        }
//    }
//
//    public void addDistinctGroup(HashSet<Value> set) {
//        mDistinctGroups.add(new HashSet<>(set));
//    }
//
//    public void addValue(Value value, ArrayList<Value> attrs) {
//        if (!value.getType().isPointerTy()) throw new RuntimeException("wrong type");
//        mPointerAttributes.put(value, new ArrayList<>(attrs));
//    }
//
//    public boolean isDistinct(Value v1, Value v2) {
//
//    }
//
//    public boolean appendAttr(Value value, ArrayList<Value> attrs) {
//
//    }
//
//    public boolean appendAttr(Value value, Value newAttr) {
//
//    }
//
//    public ArrayList<Value> inheritFrom(Value ptr) {
//
//    }
//}
