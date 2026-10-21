package io.github.yjhhhaaa06.springbook.ds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MinHeap} 的纯单元测试：不启 Spring、不连数据库。
 */
class MinHeapTest {

    private static final Comparator<Integer> NATURAL = Comparator.naturalOrder();

    @Test
    @DisplayName("不设上限时按升序出堆")
    void unboundedDrainsAscending() {
        MinHeap<Integer> heap = new MinHeap<>(0, NATURAL);
        for (int value : new int[]{5, 3, 8, 1, 9, 2}) {
            assertThat(heap.offer(value)).isTrue();
        }
        assertThat(heap.size()).isEqualTo(6);
        assertThat(heap.drainAscending()).containsExactly(1, 2, 3, 5, 8, 9);
        assertThat(heap.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("容量为 k 时只保留最大的 k 个")
    void boundedKeepsLargest() {
        MinHeap<Integer> heap = new MinHeap<>(3, NATURAL);
        for (int value : new int[]{5, 1, 9, 7, 3, 8}) {
            heap.offer(value);
        }
        assertThat(heap.size()).isEqualTo(3);
        assertThat(heap.drainAscending()).containsExactly(7, 8, 9);
    }

    @Test
    @DisplayName("堆顶是已保留元素里最小的那个")
    void peekReturnsSmallestRetained() {
        MinHeap<Integer> heap = new MinHeap<>(3, NATURAL);
        heap.offer(10);
        heap.offer(30);
        heap.offer(20);
        assertThat(heap.peek()).isEqualTo(10);

        heap.offer(40);
        assertThat(heap.peek()).isEqualTo(20);
    }

    @Test
    @DisplayName("满堆时比堆顶小的候选被丢弃，返回值是「是否入选」")
    void fullHeapRejectsSmaller() {
        MinHeap<Integer> heap = new MinHeap<>(2, NATURAL);
        heap.offer(10);
        heap.offer(20);

        assertThat(heap.offer(5)).isFalse();
        assertThat(heap.offer(10)).isFalse();
        assertThat(heap.size()).isEqualTo(2);

        assertThat(heap.offer(11)).isTrue();
        assertThat(heap.drainAscending()).containsExactly(11, 20);
    }

    @Test
    @DisplayName("空堆的 poll / peek 返回 null，不抛异常")
    void emptyHeapIsSafe() {
        MinHeap<Integer> heap = new MinHeap<>(2, NATURAL);
        assertThat(heap.poll()).isNull();
        assertThat(heap.peek()).isNull();
        assertThat(heap.drainAscending()).isEmpty();
    }

    @Test
    @DisplayName("重复值不影响堆序与容量")
    void duplicatesAreAllowed() {
        MinHeap<Integer> heap = new MinHeap<>(4, NATURAL);
        for (int i = 0; i < 4; i++) {
            heap.offer(7);
        }
        assertThat(heap.drainAscending()).containsExactly(7, 7, 7, 7);
    }

    @Test
    @DisplayName("比较器决定堆顶，而不是元素的自然顺序")
    void comparatorDefinesOrder() {
        // 按长度比较：容量 2 时应保留最长的两个，而不是字典序最大的两个
        MinHeap<String> heap = new MinHeap<>(2, Comparator.comparingInt(String::length));
        heap.offer("aaa");
        heap.offer("b");
        heap.offer("cc");

        assertThat(heap.drainAscending()).containsExactly("cc", "aaa");
    }

    @Test
    @DisplayName("顺序写入后按逆序出堆，验证建堆与下沉正确")
    void reverseInputDrainsForward() {
        MinHeap<Integer> heap = new MinHeap<>(0, NATURAL);
        for (int value = 50; value >= 1; value--) {
            heap.offer(value);
        }
        assertThat(heap.drainAscending()).hasSize(50).startsWith(1, 2, 3).endsWith(50);
    }

    @Test
    @DisplayName("无上限堆在扩容后仍保持堆序")
    void growsWithoutLosingOrder() {
        MinHeap<Integer> heap = new MinHeap<>(0, NATURAL);
        int count = 100;
        for (int i = count; i >= 1; i--) {
            heap.offer(i);
        }
        List<Integer> drained = heap.drainAscending();
        assertThat(drained).hasSize(count);
        assertThat(drained.getFirst()).isEqualTo(1);
        assertThat(drained.getLast()).isEqualTo(count);
    }
}
