package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

public class SetImpl implements Set {
    private interface NodeState {
        // marker interface
    }

    private static class Removed implements NodeState {
        final Node node;

        private Removed(Node node) {
            this.node = node;
        }
    }

    private static class Node implements NodeState {
        int x;
        AtomicRef<NodeState> next;

        private Node(int x, Node next) {
            this.next = new AtomicRef<NodeState>(next);
            this.x = x;
        }
    }

    private static class Window {
        Node cur;
        Node next;
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    /**
     * Returns the {@link Window}, where cur.x < x <= next.x
     */
    private Window findWindow(int x) {
        while (true) {
            Window w = new Window();
            w.cur = head;
            w.next = (Node) w.cur.next.getValue();
            boolean retryExternalWhile = false;
            while (w.next.x < x) {
                NodeState node = w.next.next.getValue();
                if (node instanceof Removed) {
                    Node nextNode = ((Removed) node).node;
                    if (!w.cur.next.compareAndSet(w.next, nextNode)) {
                        retryExternalWhile = true;
                        break;
                    }
                    w.next = nextNode;
                } else {
                    w.cur = w.next;
                    w.next = (Node) node;
                }
            }
            if (retryExternalWhile) {
                continue;
            }
            return w;
        }
    }

    @Override
    public boolean add(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.next.getValue() instanceof Node && w.next.x == x) {
                return false;
            }
            Node node = new Node(x, w.next);
            if (w.cur.next.compareAndSet(w.next, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.x != x) {
                return false;
            }

            NodeState node = w.next.next.getValue();
            if (node instanceof Removed) {
                return false;
            }
            if (w.next.next.compareAndSet(node, new Removed((Node) node))) {
                w.cur.next.compareAndSet(w.next, node);
                return true;
            }

        }
    }

    @Override
    public boolean contains(int x) {
        Window w = findWindow(x);
        return w.next.next.getValue() instanceof Node && w.next.x == x;
    }
}