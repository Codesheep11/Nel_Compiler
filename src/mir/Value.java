package mir;

import utils.NelLinkedList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class Value extends NelLinkedList.NelLinkNode {

    protected String name;
    protected final Type type;

    /**
     * 维护了集合性质，乱序，不重地维护了双向边关系
     * 对于可能重边的情况，在具体的Instruction 类里维护
     */
    private final LinkedList<Use> uses;

    public Value(String name, Type type) {
        this.name = name;
        this.type = type;
        uses = new LinkedList<>();
    }

    public Value(Type type) {
        this.type = type;
        uses = new LinkedList<>();
        name = "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return name;
    }

    public LinkedList<Use> getUses() {
        return uses;
    }

    public boolean use_empty() {
        return uses.isEmpty();
    }


    public Use use_begin() {
        return uses.getFirst();
    }

    public void use_add(Use use) {
        if (!uses.contains(use)) {
            uses.add(use);
        }
    }

    public void use_remove(Use use) {
        uses.remove(use);
    }

    public Type getType() {
        return type;
    }

    /*
     * 对 Value 使用的全替换的操作，通过单点调用User的replaceUseOfWith实现
     * 对于被替换的Value v, 因为所有的use 边都被替换，所以v的use集合为空
     * 强调：该方法仅为模版方法，对于一条具体的实例指令，需要在其类中重写该方法
     */
    public void replaceAllUsesWith(Value v) {
        while (!use_empty()) {
            Use use = use_begin();
            // 每次必然删该条边，
            use.getUser().replaceUseOfWith(this, v);
        }
    }

    public void use_clear() {
        Iterator<Use> it = uses.iterator();
        while (it.hasNext()) {
            Use use = it.next();
            use.getUser().use_remove(new Use(use.getUser(), this));
            it.remove();
        }
    }

    /**
     * 释放该Value的所有使用
     */
    public void release() {
        use_clear();
    }

    /**
     * 删除该Value的所有使用并移除链表关系<br>
     * <br>
     * <p>
     * Note: 如果仅删除使用请使用release方法！
     * </p>
     */
    public void delete() {
        use_clear();
        this.remove();
    }

    public ArrayList<Instruction> getUsers() {
        ArrayList<Instruction> users = new ArrayList<>();
        for (Use use : uses) users.add((Instruction) use.getUser());
        return users;
    }

}
