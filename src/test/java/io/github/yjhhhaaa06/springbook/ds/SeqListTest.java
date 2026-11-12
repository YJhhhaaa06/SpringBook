package io.github.yjhhhaaa06.springbook.ds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SeqList} 的纯单元测试：不启 Spring、不连数据库，只验证结构本身。
 *
 * <p>覆盖三件容易被「能跑就行」掩盖的事：有序性在插入/删除后是否真的保持、
 * 二分未命中时返回的插入点是否正确、键变化时是否 fail-fast 而不是静默错位。
 */
class SeqListTest {

    /** 用小对象而不是裸 String，才能验证「同键多元素」的先后顺序 */
    private record Item(int key, String name) {
    }

    private static SeqList<Item, Integer> newList() {
        return new SeqList<>(Item::key, Comparator.naturalOrder());
    }

    @Test
    @DisplayName("乱序插入后按键升序排列，重复键按插入先后排在后面")
    void addKeepsOrderAndAppendsDuplicates() {
        SeqList<Item, Integer> list = newList();
        list.add(new Item(3, "c"));
        list.add(new Item(1, "a1"));
        list.add(new Item(2, "b"));
        list.add(new Item(1, "a2"));

        assertThat(list.size()).isEqualTo(4);
        assertThat(names(list)).containsExactly("a1", "a2", "b", "c");
    }

    @Test
    @DisplayName("二分命中返回下标，未命中返回 -(插入点)-1")
    void indexOfFollowsBinarySearchConvention() {
        SeqList<Item, Integer> list = newList();
        for (int key : new int[]{10, 20, 30, 40}) {
            list.add(new Item(key, "k" + key));
        }

        assertThat(list.indexOf(10)).isZero();
        assertThat(list.indexOf(30)).isEqualTo(2);
        assertThat(list.indexOf(40)).isEqualTo(3);

        // 插入点分别是 0 / 1 / 4，按 Arrays.binarySearch 的约定编码为 -1 / -2 / -5
        assertThat(list.indexOf(5)).isEqualTo(-1);
        assertThat(list.indexOf(15)).isEqualTo(-2);
        assertThat(list.indexOf(50)).isEqualTo(-5);
    }

    @Test
    @DisplayName("空表查到 -1，等价于「插入点是 0」")
    void emptyListIndexOf() {
        assertThat(newList().indexOf(1)).isEqualTo(-1);
    }

    @Test
    @DisplayName("同键替换返回旧元素，且不改变位置")
    void setReplacesSameKeyInPlace() {
        SeqList<Item, Integer> list = newList();
        list.add(new Item(1, "old"));
        list.add(new Item(2, "b"));

        Item old = list.set(0, new Item(1, "new"));

        assertThat(old.name()).isEqualTo("old");
        assertThat(names(list)).containsExactly("new", "b");
    }

    @Test
    @DisplayName("键变化时 set 直接失败，避免下标与目录错位")
    void setWithChangedKeyFailsFast() {
        SeqList<Item, Integer> list = newList();
        list.add(new Item(1, "a"));

        assertThatThrownBy(() -> list.set(0, new Item(2, "b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key changed");
    }

    @Test
    @DisplayName("删除后后缀前移，顺序与长度都正确")
    void removeAtShiftsSuffix() {
        SeqList<Item, Integer> list = newList();
        for (int key = 1; key <= 4; key++) {
            list.add(new Item(key, "k" + key));
        }

        Item removed = list.removeAt(1);

        assertThat(removed.name()).isEqualTo("k2");
        assertThat(list.size()).isEqualTo(3);
        assertThat(names(list)).containsExactly("k1", "k3", "k4");
        assertThat(list.indexOf(2)).isEqualTo(-2);
    }

    @Test
    @DisplayName("扩容后已有元素不丢，且仍然有序")
    void growsBeyondInitialCapacity() {
        SeqList<Item, Integer> list = new SeqList<>(Item::key, Comparator.naturalOrder(), 2);
        for (int key = 100; key >= 1; key--) {
            list.add(new Item(key, "k" + key));
        }

        assertThat(list.size()).isEqualTo(100);
        assertThat(list.get(0).key()).isEqualTo(1);
        assertThat(list.get(99).key()).isEqualTo(100);
    }

    @Test
    @DisplayName("按下标访问越界抛 IndexOutOfBoundsException")
    void getRejectsOutOfRangeIndex() {
        SeqList<Item, Integer> list = newList();
        list.add(new Item(1, "a"));

        assertThatThrownBy(() -> list.get(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> list.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("clear 清空元素但表仍可继续使用")
    void clearResetsSize() {
        SeqList<Item, Integer> list = newList();
        list.add(new Item(1, "a"));
        list.add(new Item(2, "b"));

        list.clear();

        assertThat(list.isEmpty()).isTrue();
        assertThat(list.indexOf(1)).isEqualTo(-1);
        list.add(new Item(3, "c"));
        assertThat(names(list)).containsExactly("c");
    }

    private static List<String> names(SeqList<Item, Integer> list) {
        return java.util.stream.IntStream.range(0, list.size())
                .mapToObj(i -> list.get(i).name())
                .toList();
    }
}
