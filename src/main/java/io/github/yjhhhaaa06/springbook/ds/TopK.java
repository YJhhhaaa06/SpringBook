package io.github.yjhhhaaa06.springbook.ds;

import java.util.Comparator;
import java.util.List;

/**
 * 从任意序列里选出最大的 k 个元素，返回降序列表。
 *
 * <p>实现是"固定容量 k 的最小堆"：候选逐个进来，堆没满就入堆；满了只有比堆顶大才替换。
 * 因此时间 O(n·log k)、额外内存 O(k)，不需要把候选集复制一份再排序。
 *
 * <p>与 {@code list.sort()} 的区别不是"更快"，而是<b>不需要额外一整份排序结果</b>：
 * 当候选集本身很大、又只需要前 k 条时，排序的 O(n) 额外内存和 O(n·log n) 都是白花的。
 * 数据量小时两者没有可测差异，选它是因为语义与需求一致（只要前 k 条）。
 */
public final class TopK {

    private TopK() {
    }

    /**
     * @param candidates 候选序列，只遍历一次
     * @param k          要保留的条数；{@code <= 0} 返回空列表
     * @param order      "谁更大"的比较器，与返回顺序一致
     * @return 降序排列的前 k 个元素；候选不足 k 个时返回全部
     */
    public static <T> List<T> select(Iterable<? extends T> candidates, int k, Comparator<? super T> order) {
        if (k <= 0 || candidates == null) {
            return List.of();
        }
        MinHeap<T> heap = new MinHeap<>(k, order);
        for (T candidate : candidates) {
            heap.offer(candidate);
        }
        return heap.drainAscending().reversed();
    }
}
