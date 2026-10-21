package io.github.yjhhhaaa06.springbook.ds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TopK} 的纯单元测试。
 */
class TopKTest {

    private static final Comparator<Integer> NATURAL = Comparator.naturalOrder();

    @Test
    @DisplayName("候选多于 k 时返回最大的 k 个，降序")
    void selectsLargestKInDescendingOrder() {
        List<Integer> candidates = List.of(5, 1, 9, 7, 3, 8, 2);

        assertThat(TopK.select(candidates, 3, NATURAL)).containsExactly(9, 8, 7);
    }

    @Test
    @DisplayName("候选不足 k 个时全部返回")
    void returnsAllWhenFewerThanK() {
        assertThat(TopK.select(List.of(2, 4), 10, NATURAL)).containsExactly(4, 2);
    }

    @Test
    @DisplayName("k <= 0 或候选为空时返回空列表")
    void degenerateInputsReturnEmpty() {
        assertThat(TopK.select(List.of(1, 2, 3), 0, NATURAL)).isEmpty();
        assertThat(TopK.select(List.of(1, 2, 3), -1, NATURAL)).isEmpty();
        assertThat(TopK.select(List.of(), 3, NATURAL)).isEmpty();
    }

    @Test
    @DisplayName("与「全排序后取前 k」结果一致（含重复值与随机数据）")
    void matchesFullSort() {
        Random random = new Random(20261021L);
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            candidates.add(random.nextInt(50));
        }
        List<Integer> expected = candidates.stream()
                .sorted(Comparator.reverseOrder())
                .limit(7)
                .toList();

        assertThat(TopK.select(candidates, 7, NATURAL)).isEqualTo(expected);
    }

    @Test
    @DisplayName("只遍历候选一次，且不修改原集合")
    void iteratesOnceAndDoesNotMutate() {
        List<Integer> candidates = new ArrayList<>(List.of(3, 1, 2));
        int[] visits = {0};

        Iterable<Integer> counted = () -> new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < candidates.size();
            }

            @Override
            public Integer next() {
                visits[0]++;
                return candidates.get(index++);
            }
        };

        assertThat(TopK.select(counted, 2, NATURAL)).containsExactly(3, 2);
        assertThat(visits[0]).isEqualTo(3);
        assertThat(candidates).containsExactly(3, 1, 2);
    }

    @Test
    @DisplayName("比较器含并列时结果仍确定（由调用方给出次级比较）")
    void tiesAreBrokenByComparator() {
        record Item(String name, int score) {
        }
        Comparator<Item> order = Comparator.comparingInt(Item::score)
                .thenComparing(Item::name);

        List<Item> items = List.of(
                new Item("a", 10), new Item("c", 10), new Item("b", 10), new Item("d", 1));

        assertThat(TopK.select(items, 2, order))
                .extracting(Item::name)
                .containsExactly("c", "b");
    }
}
