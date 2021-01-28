package stack;

import kotlinx.atomicfu.AtomicRef;

import java.util.Random;

public class StackImpl implements Stack {
    private static final int ELIMINATION_SIZE = 264;
    private static final int ELIMINATION_NUMBER_OF_WAIT_ITERATIONS = 128;
    private static final int ELIMINATION_WINDOW_SIZE = 8;
    private static final boolean ELIMINATION_ENABLED = true;

    private static final EliminationArray eliminationArray = new EliminationArray(
            ELIMINATION_SIZE,
            ELIMINATION_NUMBER_OF_WAIT_ITERATIONS,
            ELIMINATION_WINDOW_SIZE
    );

    // head pointer
    private final AtomicRef<Node> head = new AtomicRef<>(null);

    @Override
    public void push(int x) {
        if (!ELIMINATION_ENABLED || !eliminationArray.push(x)) {
            while (true) {
                Node curHead = head.getValue();
                Node newNode = new Node(x, curHead);
                if (head.compareAndSet(curHead, newNode)) {
                    return;
                }
            }
        }
    }

    @Override
    public int pop() {
        if (ELIMINATION_ENABLED) {
            Integer eliminationArrayRes = eliminationArray.pop();
            if (eliminationArrayRes != null) {
                return eliminationArrayRes;
            }
        }

        while (true) {
            Node curHead = head.getValue();
            if (curHead == null) {
                return Integer.MIN_VALUE;
            }
            if (head.compareAndSet(curHead, curHead.next.getValue())) {
                return curHead.x;
            }
        }
    }

    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    public static class EliminationArray {
        private final Slot[] eliminationArray;
        private final int windowSize;
        private static final Random RANDOM = new Random();

        public EliminationArray(int size, int numberOfWaitIterations, int windowSize) {
            this.windowSize = windowSize;

            eliminationArray = new Slot[size];
            for (int i = 0; i < size; i++) {
                eliminationArray[i] = new Slot(numberOfWaitIterations);
            }
        }

        public boolean push(int x) {
            int slotIndex = RANDOM.nextInt(eliminationArray.length);
            int lowerBound = Math.max(0, slotIndex - windowSize / 2);
            int upperBound = Math.min(eliminationArray.length, slotIndex + windowSize / 2);

            for (int i = lowerBound; i < upperBound; i++) {
                int res = eliminationArray[i].push(x);
                if (res == 1) {
                    return true;
                }
                if (res == -1) {
                    return false;
                }
            }
            return false;
        }

        public Integer pop() {
            int slotIndex = RANDOM.nextInt(eliminationArray.length);
            int lowerBound = Math.max(0, slotIndex - windowSize / 2);
            int upperBound = Math.min(eliminationArray.length, slotIndex + windowSize / 2);

            for (int i = lowerBound; i < upperBound; i++) {
                Integer res = eliminationArray[i].pop();
                if (res != null) {
                    return res;
                }
            }
            return null;
        }

        private static class Slot {
            private final int numberOfWaitIterations;
            AtomicRef<Integer> value = new AtomicRef<>(null);

            public Slot(int numberOfWaitIterations) {
                this.numberOfWaitIterations = numberOfWaitIterations;
            }

            public int push(Integer x) {
                if (value.compareAndSet(null, x)) {
                    for (int i = 0; i < numberOfWaitIterations; i++) {
                        if (value.getValue() == null) {
                            return 1;
                        }
                    }
                    return value.compareAndSet(x, null) ? -1 : 1;
                }
                return 0;
            }

            public Integer pop() {
                Integer curVal = value.getValue();
                return curVal != null && value.compareAndSet(curVal, null) ? curVal : null;
            }
        }
    }
}
