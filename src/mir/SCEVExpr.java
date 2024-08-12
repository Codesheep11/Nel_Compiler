package mir;

import java.util.ArrayList;

/**
 * Chain of Recurrence
 */
public final class SCEVExpr implements Cloneable{

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

    public boolean isNotNegative() {
        if (type == SCEVType.Constant) {
            return constant >= 0;
        } else {
            for (SCEVExpr operand : operands) {
                if (!operand.isNotNegative()) {
                    return false;
                }
            }
            return true;
        }
    }

    public boolean isOddAll() {
        return (getInit() & 1) == 1 && (getStep() & 1) == 0;
    }

    public boolean isEvenAll() {
        return (getInit() & 1) == 0 && (getStep() & 1) == 0;
    }

    public boolean isInSameLoop() {
        if (type == SCEVType.Constant) {
            return true;
        } else {
            Loop loop = null;
            for (SCEVExpr operand : operands) {
                if (!operand.isInSameLoop()) {
                    return false;
                }
                if (loop == null) {
                    loop = operand.loop;
                } else {
                    if (loop != operand.loop) {
                        return false;
                    }
                }
            }
            return loop ==null || loop == this.loop;
        }
    }

    public int getInit() {
        if (type == SCEVType.Constant) {
            return constant;
        } else {
            return operands.get(0).getInit();
        }
    }

    public int getStep() {
        return calc(this, 1) - calc(this, 0);
    }

    public int getSize() {
        if (type == SCEVType.Constant) {
            return 1;
        } else {
            int sum = 0;
            for (SCEVExpr operand : operands) {
                sum += operand.getSize();
            }
            return sum;
        }
    }

    @Override
    public Object clone() {
        try {
            SCEVExpr cloned = (SCEVExpr) super.clone();
            cloned.operands = new ArrayList<>();
            for (SCEVExpr operand : this.operands) {
                cloned.operands.add((SCEVExpr) operand.clone());
            }
            cloned.constant = this.constant;
            cloned.type = this.type;
            // loop is not deep copied, just reference copied
            cloned.loop = this.loop;
            return cloned;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Clone failed");
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
