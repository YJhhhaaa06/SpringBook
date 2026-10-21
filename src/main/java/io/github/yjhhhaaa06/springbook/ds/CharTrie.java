package io.github.yjhhhaaa06.springbook.ds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 字符前缀树（Trie）。只解决一件事：给定前缀，取出以它为前缀的全部值。
 *
 * <p>为什么不实现 {@code java.util} 的集合接口：它不是一个「通用容器」，
 * 没有按下标访问、更没有迭代全部元素的语义，硬套 {@code List} 只会得到一堆
 * 语义不成立的方法。唯一借用的是 {@code TreeMap} 作为子节点表 —— 见下。
 *
 * <p><b>子节点为什么用 {@link TreeMap} 而不是数组或手写哈希表</b>：这里的形状由数据决定，
 * 一个字符节点的子节点数通常是个位数（中文书名首字重复率低），因此 Array of 65536
 * 不现实、手写开放寻址的收益为零。而 TreeMap 换来的是<b>按字符有序</b>遍历 ——
 * 这是 {@link #prefixValues} 能在凑够 limit 时立刻收手、且输出顺序稳定的前提。
 *
 * <p><b>复杂度</b>：{@code put} / {@code remove} 为 O(L·log σ)（L 为键长、σ 为子节点数）；
 * {@code prefixValues} 为 O(L·log σ + m·L)，m 为实际返回条数，而不是 O(全表)。
 *
 * <p><b>线程安全</b>：不安全。并发保护由调用方（{@code BookIndex} 的读写锁）负责。
 *
 * @param <V> 挂在键上的值；同一个键可以挂多个值（如同一书名的不同副本）
 */
public final class CharTrie<V> {

    private static final class Node<V> {
        /** 子节点按字符升序排列，保证深度优先遍历即字典序 */
        private final Map<Character, Node<V>> children = new TreeMap<>();
        /** 该节点是一个完整键的终点时非空；其他情况下为 null 而不是空表，省一份对象头 */
        private List<V> bucket;
    }

    private final Node<V> root = new Node<>();
    private int size;

    /** 不同键的数量（同一键挂多个值只计一次，与 {@code Map.size()} 同义） */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        root.children.clear();
        root.bucket = null;
        size = 0;
    }

    /**
     * 插入一个键值对。
     *
     * @return 该值原本不在这个键上时返回 true（重复插入返回 false，不产生重复项）
     */
    public boolean put(String key, V value) {
        checkKey(key);
        Objects.requireNonNull(value, "value");

        Node<V> node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.computeIfAbsent(key.charAt(i), ignored -> new Node<>());
        }
        if (node.bucket == null) {
            node.bucket = new ArrayList<>(2);
            size++;
        }
        if (node.bucket.contains(value)) {
            return false;
        }
        node.bucket.add(value);
        return true;
    }

    /**
     * 删除指定键上的某个值。键上已无值、且不再被任何更长键经过时，沿途的空节点会被剪掉，
     * 避免删除后残留一条没有终点的长链。
     */
    public boolean remove(String key, V value) {
        checkKey(key);
        Objects.requireNonNull(value, "value");

        Deque<Node<V>> path = new ArrayDeque<>(key.length() + 1);
        Node<V> node = root;
        path.push(node);
        for (int i = 0; i < key.length(); i++) {
            node = node.children.get(key.charAt(i));
            if (node == null) {
                return false;
            }
            path.push(node);
        }
        if (node.bucket == null || !node.bucket.remove(value)) {
            return false;
        }
        if (node.bucket.isEmpty()) {
            node.bucket = null;
            size--;
            prune(path);
        }
        return true;
    }

    public boolean contains(String key) {
        checkKey(key);
        Node<V> node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.get(key.charAt(i));
            if (node == null) {
                return false;
            }
        }
        return node.bucket != null;
    }

    /**
     * 前缀检索：按键的字典序返回以 {@code prefix} 为前缀的值，最多 {@code limit} 条。
     *
     * <p>先命中前缀节点，再按子节点升序做深度优先遍历；凑够 limit 立即返回，
     * 因此代价与「前缀所在子树的规模」和「实际返回条数」有关，与全表规模无关。
     */
    public List<V> prefixValues(String prefix, int limit) {
        Objects.requireNonNull(prefix, "prefix");
        if (limit <= 0) {
            return List.of();
        }
        Node<V> node = root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.get(prefix.charAt(i));
            if (node == null) {
                return List.of();
            }
        }
        List<V> out = new ArrayList<>(Math.min(limit, 16));
        collect(node, out, limit);
        return out;
    }

    private static <V> void collect(Node<V> node, List<V> out, int limit) {
        if (node.bucket != null) {
            for (V value : node.bucket) {
                if (out.size() >= limit) {
                    return;
                }
                out.add(value);
            }
        }
        for (Node<V> child : node.children.values()) {
            if (out.size() >= limit) {
                return;
            }
            collect(child, out, limit);
        }
    }

    /** 自终点向根剪枝：只有「无值且无子节点」的节点会被摘掉 */
    private static <V> void prune(Deque<Node<V>> path) {
        Node<V> node = path.pop();
        while (!path.isEmpty()) {
            if (node.bucket != null || !node.children.isEmpty()) {
                return;
            }
            Node<V> parent = path.pop();
            parent.children.values().remove(node);
            node = parent;
        }
    }

    private static void checkKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Trie 的键不能为空");
        }
    }
}
