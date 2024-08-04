package midend.Analysis;

import mir.Constant;
import mir.Value;

import java.util.HashSet;
import java.util.Map;
import java.util.Vector;

/**
 *
 */
public class AliasAnalysis {

    private static class Attribute {
        // todo : fill details
    }

    private static HashSet<Map.Entry<Value, Value>> mDistinctPairs;
    private static Vector<HashSet<Value>> mDistinctGroups;
    // todo: check this into
    private static Map<Value, Vector<Attribute>> mPointerAttributes;

    // when alias, two points to the same Memory Object
    public enum AliasResult {
        NoAlias,
        /// The two locations may or may not alias. This is the least precise
        /// result.
        MayAlias,
        /// The two locations alias, but only due to a partial overlap.
        PartialAlias,
        /// The two locations precisely alias each other.
        MustAlias,
    }

    public enum ModRefInfo {
        NoModRef,
        Ref,
        Mod,
        ModRef,
    }

    /**
     * 返回 V1, V2 指向内存的情况
     *
     * @param v1 指针
     * @param v2 指针
     * @return AliasResult
     */
    public AliasResult alias(Value v1, Value v2) {
        // required Pointer
        assert (v1.getType().isPointerTy() && v2.getType().isPointerTy()) ||
                (v1 instanceof Constant) || (v2 instanceof Constant);

        return AliasResult.NoAlias;
    }

    /***
     * 高级调用，直接查询访存情况
     * @param value 指针
     * @return ModRefInfo
     */
    public ModRefInfo getModRefInfo(Value value) {
        return ModRefInfo.NoModRef;
    }

}
