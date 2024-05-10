package utils;

import java.util.Iterator;

public class SyncLinkedList<Type extends SyncLinkedList.SyncLinkNode> implements Iterable<Type> {
    private final SyncLinkNode head;
    private final SyncLinkNode tail;

    /**
     * 新创建的链表，带 head 和 tail 节点
     */
    public SyncLinkedList() {
        head = new SyncLinkNode();
        tail = new SyncLinkNode();
        head.setNext(tail);
        tail.setPrev(head);
    }

    public Type getFirst() {
        assert head.getNext() != null;
        return (Type) head.getNext();
    }

    public Type getLast() {
        assert tail.getPrev() != null;
        return (Type) (tail.getPrev());
    }

    public void insertBefore(Type newNode, Type node) {
        newNode.setHasParent(true);
        newNode.setPrev(node.getPrev());
        newNode.setNext(node);
        node.getPrev().setNext(newNode);
        node.setPrev(newNode);
    }

    public void insertAfter(Type newNode, Type node) {
        newNode.setHasParent(true);
        newNode.setNext(node.getNext());
        newNode.setPrev(node);
        node.getNext().setPrev(newNode);
        node.setNext(newNode);
    }

    public void addFirst(Type newNode) {
        newNode.setHasParent(true);
        newNode.setPrev(head);
        newNode.setNext(head.getNext());
        head.getNext().setPrev(newNode);
        head.setNext(newNode);
    }

    public void addLast(Type newNode) {
        newNode.setHasParent(true);
        newNode.setPrev(tail.getPrev());
        newNode.setNext(tail);
        tail.getPrev().setNext(newNode);
        tail.setPrev(newNode);
    }

    public int getSize() {
        Iterator<Type> iterator = iterator();
        int size = 0;
        while(iterator.hasNext()) {
            iterator.next();
            size++;
        }
        return size;
    }

    public Type get(int idx) {
        int size = getSize();
        assert idx < size;
        int cur = 0;
        SyncLinkNode ret = getFirst();
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
    public void setEmpty() {
        head.setNext(tail);
        tail.setPrev(head);
    }

    public int find(Type node) {
        for (int i = 0; i < getSize(); i++) {
            if (get(i)==node) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将参数列表中的节点插入到当前链表尾部
     * 考虑到节点实体的唯一性，该操作会使得that链表为空
     *
     * @param that
     */
    public void concat(SyncLinkedList<Type> that) {
        if (that.isEmpty()) {
            that.setEmpty();
            return;
        }
        that.head.getNext().setPrev(this.getLast());
        that.getLast().setNext(this.tail);
        this.tail.getPrev().setNext(that.getFirst());
        this.tail.setPrev(that.getLast());
        // 销毁
        that.setEmpty();
    }

    public boolean isEmpty() {
        if (getSize() != 0) {
            return false;
        } else {
            assert head.getNext() == tail;
            assert tail.getPrev() == head;
            return true;
        }
    }

    @Override
    public Iterator<Type> iterator() {
        return new IIterator();
    }

    class IIterator implements Iterator<Type> {
        //实现新的迭代器
        SyncLinkNode cur;

        IIterator() {
            cur = head;
        }

        @Override
        public boolean hasNext() {
            return cur.getNext() != tail && cur.next.next != null;
        }

        @Override
        public Type next() {
            cur = cur.getNext();
            return (Type) cur;
        }

        /**
         * 迭代器允许循环中删除元素
         */
        @Override
        public void remove() {
            cur.getPrev().setNext(cur.getNext());
            cur.getNext().setPrev(cur.getPrev());
        }


    }

    public static class SyncLinkNode {
        private SyncLinkNode prev;
        private SyncLinkNode next;
        /**
         * 用于标记该节点是否存在于某个链表中
         */
        private boolean hasParent = false;

        public SyncLinkNode() {
            prev = null;
            next = null;
        }

        void setPrev(SyncLinkNode prev) {
            this.prev = prev;
        }

        void setNext(SyncLinkNode next) {
            this.next = next;
        }

        /**
         * 在所在 SyncLinkedList中是否存在下一个非空节点
         * @return true if exist
         */
        public boolean hasNext() {
            assert next != null;
            return next.next != null;
        }

        public void setHasParent(boolean hasParent) {
            this.hasParent = hasParent;
        }

        public SyncLinkNode getPrev() {
            return prev;
        }

        public SyncLinkNode getNext() {
            return next;
        }

        /**
         * 仅当存在父链表的时候才允许删除
         */
        public void remove() {
            if(!hasParent) {
                return;
            }
            assert prev != null;
            assert next != null;
            prev.setNext(next);
            next.setPrev(prev);
            hasParent = false;
        }
    }

}
