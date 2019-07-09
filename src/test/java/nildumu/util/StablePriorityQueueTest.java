package nildumu.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StablePriorityQueueTest {

    @Test
    public void testMultipleElements(){
        test(Arrays.asList(1, 2, 3, 3), Arrays.asList(1, 2, 3, 3), Integer::compareTo);
    }

    @Test
    public void testStable(){
        test(Arrays.asList(1, 2, 3), Arrays.asList(3, 2, 1), (e1, e2) -> 0);
    }

    <E> void test(List<E> expected, List<E> elements, Comparator<E> comparator){
        StablePriorityQueue<E> queue = new StablePriorityQueue<>(comparator);
        elements.forEach(queue::offer);
        List<E> actual = new ArrayList<>();
        while (!queue.isEmpty()){
            actual.add(queue.poll());
        }
        assertEquals(expected, actual);
    }
}