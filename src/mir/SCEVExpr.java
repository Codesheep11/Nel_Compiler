package mir;

import java.util.ArrayList;

/**
 * Chain of Recurrence
 * @author Srchycz
 */
public final class SCEVExpr {

    public ArrayList<SCEVExpr> operands;

    public int constant;

    public SCEVType type;

    public Loop loop;

    public enum SCEVType {
        Constant, AddRec
    }

    public SCEVExpr(SCEVType type) {
        this.type = type;
        this.operands = new ArrayList<>();
    }

    public SCEVExpr(SCEVType type, int constant) {
        this.constant = constant;
        this.type = type;
        this.operands = new ArrayList<>();
    }


    private  static int k = 0;
    private static int n = 0;
    private static int c = 1;
    public static int calc(SCEVExpr expr, int N) {
        n = N;
        k = 0;
        c = 1;
        return dfs(expr);
    }

    public static int dfs(SCEVExpr now) {
        if (k > n) return 0;
        if (now.type == SCEVType.Constant) {
            int oldc = c;
            c = (n - k) * c / (k + 1);
            k ++;
            return now.constant * oldc;
        }
        int sum = 0;
        for (SCEVExpr operand : now.operands) {
            sum += dfs(operand);
        }
        return sum;
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
