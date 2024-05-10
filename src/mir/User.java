package mir;
import java.util.LinkedList;

public class User extends Value {

    /**
     * 维护了集合性质，乱序，不重地维护了操作数
     */
    private final LinkedList<Value> operands;

    protected User(String name, Type type) {
        super(name, type);
        operands = new LinkedList<>();
    }

    protected User(Type type) {
        super(type);
        operands = new LinkedList<>();
    }

    public LinkedList<Value> getOperands() {
        return operands;
    }

    /**
     * 维护了双向边关系
     */
    public void addOperand(Value operand) {
        // 如果已包含直接返回
        if (operands.contains(operand)) {
            return;
        }
        operands.add(operand);
        // 同时维护operand 的 use 关系
        Use use = new Use(this, operand);
        operand.use_add(use);
    }

    /**
     * 同时删除双向边关系
     *
     * @param value
     * @param v
     */
    public void replaceUseOfWith(Value value, Value v) {
        // 在 Value 的 operands 中更新
        value.use_remove(new Use(this, value));
        operands.remove(value);
        addOperand(v);
    }
}
