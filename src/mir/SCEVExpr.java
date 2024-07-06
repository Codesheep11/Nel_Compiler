package mir;

import java.util.ArrayList;

/**
 * Chain of Recurrence
 * @author Srchycz
 */
public final class SCEVExpr {
//    struct SCEV final {
//        std::vector<SCEV*> operands;
//        intmax_t constant;
//        SCEVInstID instID;
//    const Loop* loop;
//    };

    public ArrayList<SCEVExpr> operands;

    public long constant;

    public SCEVType type;

    public Loop loop;

    public enum SCEVType {
        Constant, AddRec
    }

    public SCEVExpr(SCEVType type) {
        this.type = type;
        this.operands = new ArrayList<>();
    }

//    public String toString() {
//        StringBuilder sb = new StringBuilder();
//        if (type == SCEVType.Constant) {
//            return String.valueOf(constant);
//        } else {
//            return "AddRec";
//        }
//    }
}
