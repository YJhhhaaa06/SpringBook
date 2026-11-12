package io.github.yjhhhaaa06.springbook.ds;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

/**
 * 顺序表：元素连续存放在一块可扩容的数组里，并始终按键升序排列。
 *
 * <p><b>为什么手写而不是直接用 {@code java.util.ArrayList}</b>：本类是被考察的数据结构本身 ——
 * 有序插入、二分定位、下标访问、删除后的元素搬移都在这里实现。业务代码可以把 JDK 容器
 * 当作实现手段（例如 {@code BookMemoryIndex} 里的「id → 下标」目录），但线性表本身不外包。
 *
 * <p><b>为什么要求有序</b>：按书号取书是这次要接的主链路，只有有序才能二分到 O(log n)；
 * 无序表只能线性扫描 O(n)。代价是插入要搬移元素，复杂度仍是 O(n)，与无序表插入同阶。
 * 也就是说，「有序」买到的是查找，付出的只是常数级的搬移，不改变插入的复杂度量级。
 *
 * <p><b>为什么把键从元素里拆出来（{@code T} 与 {@code K} 两个类型参数）</b>：排序和定位只认键，
 * 不要求元素实现 {@code Comparable}。{@code Book} 是持久化实体，让它为了排序去实现接口
 * 是污染领域模型；把键提取器放进构造参数，领域对象保持干净，也能用同一个表装别的类型。
 *
 * <p><b>复杂度</b>：{@link #indexOf} O(log n)；{@link #get} O(1)；
 * {@link #add} / {@link #removeAt} O(n)（定位 O(log n) + 搬移 O(n)）。
 *
 * <p><b>线程安全</b>：不安全。并发保护由调用方负责（{@code BookMemoryIndex} 用读写锁）。
 *
 * @param <T> 元素类型
 * @param <K> 排序键类型
 */
public final class SeqList<T, K> {

    private static final int DEFAULT_CAPACITY = 16;

    private final Function<? super T, ? extends K> keyOf;
    private final Comparator<? super K> order;

    private Object[] elements;
    private int size;

    public SeqList(Function<? super T, ? extends K> keyOf, Comparator<? super K> order) {
        this(keyOf, order, DEFAULT_CAPACITY);
    }

    public SeqList(Function<? super T, ? extends K> keyOf, Comparator<? super K> order, int initialCapacity) {
        this.keyOf = Objects.requireNonNull(keyOf, "keyOf");
        this.order = Objects.requireNonNull(order, "order");
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive: " + initialCapacity);
        }
        this.elements = new Object[initialCapacity];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 按下标访问。数组连续存放，因此是 O(1) —— 这是顺序表相对链表唯一无法被替代的优势。
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        Objects.checkIndex(index, size);
        return (T) elements[index];
    }

    /**
     * 有序插入：先二分出第一个「大于」该键的位置，再把后缀整体后移一位。
     *
     * <p>用 upperBound 而不是 lowerBound，是为了让键相同的元素保持插入先后顺序
     * （后来的排在已有元素之后）。业务上 ISBN 唯一，这条语义用不到，但线性表本身
     * 不该假设键唯一，否则它就不是通用容器了。
     *
     * @return 元素落到的下标；调用方若维护了「键 → 下标」的目录，需要据此修复后缀
     */
    public int add(T value) {
        Objects.requireNonNull(value, "value");
        K key = keyOf.apply(value);
        int position = upperBound(key);
        ensureCapacity(size + 1);
        System.arraycopy(elements, position, elements, position + 1, size - position);
        elements[position] = value;
        size++;
        return position;
    }

    /**
     * 同键替换：只允许替换排序键相同的元素，返回被替换掉的旧元素。
     *
     * <p>键变了就必须 {@link #removeAt} + {@link #add}，因为那会改变元素在表里的位置。
     * 这里不做「自动重排」：静默重排会让调用方以为下标没变，而它维护的目录已经错位。
     * 与其猜，不如让越界的使用方式直接失败。
     *
     * @throws IllegalArgumentException 新元素的键与旧元素不同
     */
    public T set(int index, T value) {
        Objects.checkIndex(index, size);
        Objects.requireNonNull(value, "value");
        T old = elementAt(index);
        K oldKey = keyOf.apply(old);
        K newKey = keyOf.apply(value);
        if (order.compare(oldKey, newKey) != 0) {
            throw new IllegalArgumentException("key changed at index " + index
                    + ": " + oldKey + " -> " + newKey + "，键变化必须 removeAt + add");
        }
        elements[index] = value;
        return old;
    }

    /**
     * 删除指定下标并返回它，后缀整体前移一位。
     */
    public T removeAt(int index) {
        Objects.checkIndex(index, size);
        T removed = elementAt(index);
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(elements, index + 1, elements, index, moved);
        }
        elements[--size] = null;
        return removed;
    }

    /**
     * 二分查找。
     *
     * @return 命中时返回下标；未命中返回 {@code -(插入点) - 1}，与
     *         {@link Arrays#binarySearch} 的约定一致，便于调用方直接反推插入位置
     */
    public int indexOf(K key) {
        Objects.requireNonNull(key, "key");
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = order.compare(keyOf.apply(elementAt(mid)), key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    /** 清空但保留已分配的容量，供索引重建复用，避免反复申请大数组 */
    public void clear() {
        Arrays.fill(elements, 0, size, null);
        size = 0;
    }

    // ---------- 内部 ----------

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) elements[index];
    }

    /** 第一个「大于」key 的下标；相等元素会排在它前面，因此插入点落在相等区间末尾 */
    private int upperBound(K key) {
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (order.compare(keyOf.apply(elementAt(mid)), key) <= 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * 扩容到 1.5 倍（与 ArrayList 同策略：摊还 O(1)，又不像翻倍那样容易浪费一半空间）。
     * 目标容量仍不够时直接取所需值，避免小步多次扩容。
     */
    private void ensureCapacity(int required) {
        if (required <= elements.length) {
            return;
        }
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity < required) {
            newCapacity = required;
        }
        if (newCapacity < 0) {
            newCapacity = Integer.MAX_VALUE;
        }
        elements = Arrays.copyOf(elements, newCapacity);
    }
}
