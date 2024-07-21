package midend.Analysis.Alias;

import midend.Util.MemoryLocation;
import mir.Constant;
import mir.Value;

public class AliasAnalysis {

    // when alias, two points to the same Memory Object
    public enum AliasResult {
        NoAlias ,
        /// The two locations may or may not alias. This is the least precise
        /// result.
        MayAlias,
        /// The two locations alias, but only due to a partial overlap.
        PartialAlias,
        /// The two locations precisely alias each other.
        MustAlias,
    }

    public AliasResult alias(Value v1, Value v2){
        // required Pointer
        assert (v1.getType().isPointerTy() && v2.getType().isPointerTy()) ||
                (v1 instanceof Constant) || (v2 instanceof Constant);



        return AliasResult.NoAlias;
    }
}
