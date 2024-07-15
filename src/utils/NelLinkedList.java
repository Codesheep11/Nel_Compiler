package utils;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * NEL-链表
 * @param <Type>
 * @author Srchycz
 * <br>
 * Note: 如需增加需求最好不要自行修改，以免破坏链表结构的安全性与稳定性
 */
public class NelLinkedList<Type extends NelLinkedList.NelLinkNode> implements Iterable<Type> {
    private final NelLinkNode head;
    private final NelLinkNode tail;
    private int size;
    private int modCount;

    /**
     * 新创建的链表，带 head 和 tail 节点
     */
    public NelLinkedList() {
        head = new NelLinkNode();
        tail = new NelLinkNode();
        head.setNext(tail);
        tail.setPrev(head);
        size = 0;
        modCount = 0;
    }

    @SuppressWarnings("unchecked")
    public Type getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return (Type) head.getNext();
    }

    /**
     * 删除最后一个节点
     */
    public void removeLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        // 调用 NelLinkNode 的 remove 方法 自动维护链表大小 和 modCount
        tail.prev.remove();
    }

    @SuppressWarnings("unchecked")
    public Type getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return (Type) (tail.prev);
    }

    public void insertBefore(Type newNode, Type node) {
        newNode.setParent(this);
        newNode.setPrev(node.prev);
        newNode.setNext(node);
        node.getPrev().setNext(newNode);
        node.setPrev(newNode);
        ++ size;
        ++ modCount;
    }

    public void insertAfter(Type newNode, Type node) {
        newNode.setParent(this);
        newNode.setNext(node.next);
        newNode.setPrev(node);
        node.getNext().setPrev(newNode);
        node.setNext(newNode);
        ++ size;
        ++ modCount;
    }

    public void addFirst(Type newNode) {
        newNode.setParent(this);
        newNode.setPrev(head);
        newNode.setNext(head.next);
        head.getNext().setPrev(newNode);
        head.setNext(newNode);
        ++ size;
        ++ modCount;
    }

    public void addLast(Type newNode) {
        newNode.setParent(this);
        newNode.setPrev(tail.prev);
        newNode.setNext(tail);
        tail.prev.setNext(newNode);
        tail.setPrev(newNode);
        ++ size;
        ++ modCount;
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    public Type get(int idx) {
        if (idx < 0 || idx >= size) {
            throw new IndexOutOfBoundsException();
        }
        int cur = 0;
        NelLinkNode ret = getFirst();
        while (cur < idx) {
            ret = ret.getNext();
            cur++;
        }
        return (Type) ret;
    }

    /**
     * todo: 可能存在一致性问题
     * 仅标注头尾节点
     */
    public void clear() {
        this.forEach(node -> node.parent = null);
        head.setNext(tail);
        tail.setPrev(head);
        size = 0;
        modCount++;
    }

    /**
     * 将参数列表中的节点插入到当前链表尾部
     * 考虑到节点实体的唯一性，该操作会使得that链表为空
     * @deprecated 仅保留供 Visitor调用
     */
    @Deprecated // 仅保留供 Visitor调用
    public void concat(NelLinkedList<Type> that) {
        if (this == that) {
            throw new IllegalArgumentException("Cannot concat the same list!");
        }
        if (that.isEmpty()) {
            that.clear();
            modCount++;
            return;
        }
        that.forEach(node -> node.parent = this);

        that.head.next.setPrev(this.tail.prev);
        that.tail.prev.setNext(this.tail);
        this.tail.prev.setNext(that.head.next);
        this.tail.setPrev(that.tail.prev);
        size += that.size();
        // 销毁
        that.clear();
        modCount++;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Iterator<Type> iterator() {
        return new NelIterator();
    }

    class NelIterator implements Iterator<Type> {
        //实现新的迭代器
        NelLinkNode cur;
        NelLinkNode nxt;
        int expectedModCount = modCount;

        NelIterator() {
            cur = head;
            nxt = head.next;
        }

        @Override
        public boolean hasNext() {
            return nxt != null && !nxt.equals(tail);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Type next() {
            checkForComodification();
            cur = nxt;
            nxt = nxt.next;
            return (Type) cur;
        }

        /**
         * 迭代器允许循环中删除元素
         */
        @Override
        public void remove() {
            if (cur == head || cur == tail || cur == null) {
                throw new IllegalStateException();
            }
            checkForComodification();
            cur.remove();
            expectedModCount = modCount;
            // 禁止通过迭代器再访问该节点
            cur = null;
        }

        /**
         * 检查是否在迭代过程中被非迭代器方法修改
         * @throws ConcurrentModificationException 并发修改异常
         */
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    public static class NelLinkNode {
        /**
         * 直接访问成员和调用public方法访问的区别: 是否需要经过安全检查
         * 直接访问成员仅推荐在清楚知道后果时使用
         */
        public NelLinkedList<? extends NelLinkNode> parent;
        public NelLinkNode prev;
        public NelLinkNode next;

        public NelLinkNode() {
            parent = null;
            prev = null;
            next = null;
        }

        void setPrev(NelLinkNode prev) {
            this.prev = prev;
        }

        void setNext(NelLinkNode next) {
            this.next = next;
        }

        public void setParent(NelLinkedList<? extends NelLinkNode> list) {
            if (this.parent != null) {
                System.out.println("Warning: Node already has a parent!");
            }
            this.parent = list;
        }

        public NelLinkNode getPrev() {
            return prev;
        }

        public NelLinkNode getNext() {
            return next;
        }

        public boolean hasNext() {
            return next != null && next.next != null;
        }

        /**
         * 仅当存在父链表的时候才允许删除
         * 自动维护链表大小 和 modCount
         */
        public void remove() {
            if (parent == null) {
                throw new IllegalStateException("Node has no parent!");
            }
            if (prev == null || next == null) {
                throw new IllegalStateException("Node is not in the list!");
            }
            prev.setNext(next);
            next.setPrev(prev);
            parent.size--;
            parent.modCount++;
            parent = null;
        }
    }

}