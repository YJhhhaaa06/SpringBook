package io.github.yjhhhaaa06.springbook.ds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CharTrie} 的纯单元测试：不启 Spring、不连数据库，只验证结构本身。
 */
class CharTrieTest {

    @Test
    @DisplayName("前缀检索按键的字典序返回，且短键在前")
    void prefixValuesAreLexicographic() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("java", "java");
        trie.put("javascript", "javascript");
        trie.put("javac", "javac");
        trie.put("go", "go");

        assertThat(trie.prefixValues("java", 10)).containsExactly("java", "javac", "javascript");
        assertThat(trie.prefixValues("jav", 10)).containsExactly("java", "javac", "javascript");
    }

    @Test
    @DisplayName("凑够 limit 就停，不会扫完整个子树")
    void limitStopsEarly() {
        CharTrie<Integer> trie = new CharTrie<>();
        for (int i = 0; i < 100; i++) {
            trie.put("k" + i, i);
        }

        assertThat(trie.prefixValues("k", 3)).hasSize(3);
        assertThat(trie.prefixValues("k", 0)).isEmpty();
    }

    @Test
    @DisplayName("同一书名可以挂多本书，重复插入同一本书不会产生重复项")
    void sameKeyHoldsMultipleValues() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("三体", "978-1");
        trie.put("三体", "978-2");
        assertThat(trie.put("三体", "978-1")).isFalse();

        assertThat(trie.prefixValues("三体", 10)).containsExactly("978-1", "978-2");
        assertThat(trie.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("前缀比键更长时，只返回与键完全相等的那条")
    void longerPrefixMatchesExactKeyOnly() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("三体", "a");
        trie.put("三体2", "b");

        assertThat(trie.prefixValues("三体2", 10)).containsExactly("b");
        assertThat(trie.prefixValues("三体3", 10)).isEmpty();
    }

    @Test
    @DisplayName("删除键上的唯一值后，沿途空节点被剪掉")
    void removePrunesEmptyBranch() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("abc", "v");
        trie.put("abd", "w");

        assertThat(trie.remove("abc", "v")).isTrue();
        assertThat(trie.contains("abc")).isFalse();
        assertThat(trie.prefixValues("abc", 10)).isEmpty();
        assertThat(trie.size()).isEqualTo(1);
        // 共同前缀 a → ab 仍被 abd 需要，不能剪掉
        assertThat(trie.prefixValues("ab", 10)).containsExactly("w");

        assertThat(trie.remove("abd", "w")).isTrue();
        assertThat(trie.isEmpty()).isTrue();
        assertThat(trie.prefixValues("a", 10)).isEmpty();
    }

    @Test
    @DisplayName("同一键删掉一个值后，另一个值仍然可检索")
    void removeKeepsSiblingValue() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("三体", "978-1");
        trie.put("三体", "978-2");

        assertThat(trie.remove("三体", "978-1")).isTrue();
        assertThat(trie.prefixValues("三体", 10)).containsExactly("978-2");
        assertThat(trie.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("删除不存在的键或值返回 false，不改变结构")
    void removeMissingReturnsFalse() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("abc", "v");

        assertThat(trie.remove("ab", "v")).isFalse();
        assertThat(trie.remove("abc", "other")).isFalse();
        assertThat(trie.remove("abcd", "v")).isFalse();
        assertThat(trie.prefixValues("abc", 10)).containsExactly("v");
    }

    @Test
    @DisplayName("clear 后结构与计数一起归零")
    void clearResetsEverything() {
        CharTrie<Integer> trie = new CharTrie<>();
        trie.put("a", 1);
        trie.put("b", 2);
        trie.clear();

        assertThat(trie.isEmpty()).isTrue();
        assertThat(trie.prefixValues("a", 10)).isEmpty();
    }

    @Test
    @DisplayName("空键直接拒绝，避免出现无意义的根节点终点")
    void blankKeyRejected() {
        CharTrie<String> trie = new CharTrie<>();
        assertThatThrownBy(() -> trie.put("", "v")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trie.prefixValues(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("prefixValues 返回独立列表，外部修改不影响结构")
    void resultIsIndependent() {
        CharTrie<String> trie = new CharTrie<>();
        trie.put("a", "v");
        List<String> hits = trie.prefixValues("a", 10);
        hits.clear();

        assertThat(trie.prefixValues("a", 10)).containsExactly("v");
    }
}
