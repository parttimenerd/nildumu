package nildumu.util;

import java.util.*;

/**
 * A stable priority queue (fifo queue per level, defined by the comparator, higher level first)
 */
public class StablePriorityQueue<E> extends AbstractQueue<E> {

    private int counter = 0;

    private static class Entry<E> {
        final int counter;
        final E e;

        private Entry(int counter, E e) {
            this.counter = counter;
            this.e = e;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?> entry = (Entry<?>) o;
            return Objects.equals(e, entry.e);
        }

        @Override
        public int hashCode() {
            return Objects.hash(e);
        }
    }

    private final PriorityQueue<Entry<E>> queue;

    public StablePriorityQueue(Comparator<E> comparator){
        queue = new PriorityQueue<>(new TreeSet<>((e1, e2) -> {
            int res = comparator.compare(e1.e, e2.e);
            if (res == 0){
                return Integer.compare(e2.counter, e1.counter);
            }
            return res;
        }));
    }

    @Override
    public Iterator<E> iterator() {
        return queue.stream().map(e -> e.e).iterator();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean offer(E e) {
        return queue.offer(new Entry<>(counter++, e));
    }

    @Override
    public E poll() {
        return queue.poll().e;
    }

    @Override
    public E peek() {
        return queue.peek().e;
    }
}
