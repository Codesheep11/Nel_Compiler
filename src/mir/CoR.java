package mir;

import java.util.ArrayList;

/**
 * Chain of Recurrence
 * 考虑符号化
 */
public class CoR {

    public final ArrayList<CoR> operands;

    public Value val;

    public final CoRType type;

    public Loop loop;

    public enum CoRType {
        Value, AddRec
    }

    public CoR(CoRType type) {
        this.type = type;
        this.operands = new ArrayList<>();
    }

    public CoR(CoRType type, Value value) {
        this.val = value;
        this.type = type;
        this.operands = new ArrayList<>();
    }

    public Value getInit() {
        if (type == CoRType.Value) {
            return val;
        } else {
            return operands.get(0).getInit();
        }
    }
}
