package io.github.yjhhhaaa06.springbook.ds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 二叉最小堆（数组实现）。用于「容量固定、只保留最大的 k 个」的 top-K 选择。
 *
 * <p><b>为什么是"有界"的</b>：这个堆存在的理由是 top-K —— 堆里最多留 k 个元素，
 * 堆顶永远是这 k 个里最小的那个，新候选只要比堆顶大就替换掉堆顶。
 * 因此 {@link #offer} 在容量已满时返回 false（候选被丢弃），而不是扩容。
 * 这比"先全部入堆再取 k 次"省下 O(n-k) 的插入代价，也让内存稳定在 O(k)。
 *
 * <p><b>为什么不实现 {@code java.util.Queue}</b>：{@code Queue} 的契约是"能一直放"，
 * 而这里的核心语义是"放不下就淘汰最小的"；两者混在一起，调用方很难看出
 * {@code offer} 返回值在此处是"是否入选"而非"是否入队成功"。
 *
 * <p><b>复杂度</b>：{@code offer} / {@code poll} 为 O(log n)，{@code peek} 为 O(1)；
 * 容量为 k 时，n 个候选的 top-K 选择是 O(n·log k)，额外内存 O(k)。
 *
 * <p><b>线程安全</b>：不安全。本项目的用法是"在一次请求内构造、用完即弃"。
 *
 * @param <T> 元素类型；比较完全由构造时传入的比较器决定
 */
public final class MinHeap<T> {

    private final Comparator<? super T> order;
    /** ≤ 0 表示不设上限（此时按 1.5 倍扩容） */
    private final int capacity;
    private Object[] items;
    private int size;

    /**
     * @param capacity 堆内最多保留的元素个数；{@code <= 0} 表示不设上限
     * @param order    决定"谁更小"的比较器，与堆顶语义一致
     */
    public MinHeap(int capacity, Comparator<? super T> order) {
        this.order = Objects.requireNonNull(order, "order");
        this.capacity = Math.max(capacity, 0);
        int initial = this.capacity > 0 ? Math.min(this.capacity, 16) : 16;
        this.items = new Object[initial];
    }

    /**
     * 放入一个候选。
     *
     * <p>容量已满时，只有比堆顶更大才会替换堆顶；否则候选直接丢弃。
     *
     * @return 该候选是否留在堆里（true = 入选或替换成功，false = 被淘汰）
     */
    public boolean offer(T item) {
        Objects.requireNonNull(item, "item");
        if (capacity > 0 && size == capacity) {
            if (order.compare(item, peek()) <= 0) {
                return false;
            }
            items[0] = item;
            siftDown(0);
            return true;
        }
        if (size == items.length) {
            grow();
        }
        items[size] = item;
        siftUp(size);
        size++;
        return true;
    }

    /** 取出并删除堆顶（最小元素）；空堆返回 null */
    @SuppressWarnings("unchecked")
    public T poll() {
        if (size == 0) {
            return null;
        }
        T top = (T) items[0];
        size--;
        if (size > 0) {
            items[0] = items[size];
            siftDown(0);
        }
        items[size] = null;
        return top;
    }

    /** 查看堆顶而不删除；空堆返回 null */
    @SuppressWarnings("unchecked")
    public T peek() {
        return size == 0 ? null : (T) items[0];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** 依次 poll，得到按比较器升序的列表（原堆被清空） */
    public List<T> drainAscending() {
        List<T> drained = new ArrayList<>(size);
        T item;
        while ((item = poll()) != null) {
            drained.add(item);
        }
        return drained;
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (compareAt(index, parent) >= 0) {
                return;
            }
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        while (true) {
            int left = (index << 1) + 1;
            if (left >= size) {
                return;
            }
            int smallest = left;
            int right = left + 1;
            if (right < size && compareAt(right, left) < 0) {
                smallest = right;
            }
            if (compareAt(smallest, index) >= 0) {
                return;
            }
            swap(index, smallest);
            index = smallest;
        }
    }

    @SuppressWarnings("unchecked")
    private int compareAt(int i, int j) {
        return order.compare((T) items[i], (T) items[j]);
    }

    private void swap(int i, int j) {
        Object tmp = items[i];
        items[i] = items[j];
        items[j] = tmp;
    }

    private void grow() {
        int next = capacity > 0 ? capacity : items.length + (items.length >> 1) + 1;
        Object[] bigger = new Object[next];
        System.arraycopy(items, 0, bigger, 0, size);
        items = bigger;
    }
}
