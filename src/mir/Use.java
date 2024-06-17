package mir;


import utils.SyncLinkedList;

import java.util.Objects;

public class Use extends SyncLinkedList.SyncLinkNode {
    private final User user;
    private final Value value;

    public Use(User user, Value value) {
        this.user = user;
        this.value = value;
    }

    public User getUser() {
        return user;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Use use = (Use) o;
        return user.equals(use.user) && value.equals(use.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, value);
    }
}
