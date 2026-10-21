package io.github.yjhhhaaa06.springbook.book.index;

import io.github.yjhhhaaa06.springbook.book.dto.BookSuggestion;
import io.github.yjhhhaaa06.springbook.ds.CharTrie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 书名联想索引：内存中的前缀树，只回答「以某串开头的书名有哪些」。
 *
 * <p><b>为什么需要它</b>：现有检索是 {@code title LIKE '%kw%'}，前导通配符让
 * {@code idx_books_title} 完全用不上（全表扫描 + filesort）。联想是逐键触发的短前缀查询，
 * 每次回车都打一次库。索引把这一步变成纯内存遍历，且联想结果<b>不回查数据库</b>
 * （{@link BookSuggestion} 不含库存等易变字段）。
 *
 * <p><b>它不承担什么</b>：权威数据仍在 MySQL。索引只是可重建的只读派生视图 ——
 * 进程重启会从 {@code books} 全量重建，任何写操作在<b>事务提交后</b>同步，
 * 因此不会出现「事务回滚但索引已改」的脏数据。库存、预约状态一律不进索引。
 */
@Component
public class BookIndex {

    private static final Logger log = LoggerFactory.getLogger(BookIndex.class);

    /** 与 books.title VARCHAR(200) 对齐：更长的部分不可能出现在库里 */
    private static final int MAX_KEY_LENGTH = 200;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final CharTrie<BookSuggestion> trie = new CharTrie<>();
    /** bookId → 归一化后的键。改名/删除时用它定位旧键，不必再查库 */
    private final Map<Long, String> keyById = new HashMap<>();

    /**
     * 全量重建（启动装载、或人工修数据后调用）。整段替换是原子的：
     * 重建期间读请求会短暂阻塞，而不是读到半成品索引。
     */
    public void rebuild(List<BookSuggestion> books) {
        lock.writeLock().lock();
        try {
            trie.clear();
            keyById.clear();
            for (BookSuggestion book : books) {
                putLocked(book);
            }
        } finally {
            lock.writeLock().unlock();
        }
        log.info("书名索引重建完成，共 {} 条", trie.size());
    }

    public void add(BookSuggestion book) {
        lock.writeLock().lock();
        try {
            putLocked(book);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 删除。{@code book} 只需带 id 与原始书名（用来定位旧键） */
    public void remove(BookSuggestion book) {
        lock.writeLock().lock();
        try {
            String key = keyById.remove(book.id());
            if (key == null) {
                key = normalize(book.title());
            }
            if (!key.isEmpty()) {
                trie.remove(key, book);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 改名：先摘旧键再挂新键，避免「旧书名还能搜到、新书名搜不到」 */
    public void update(BookSuggestion before, BookSuggestion after) {
        lock.writeLock().lock();
        try {
            String oldKey = keyById.remove(before.id());
            if (oldKey == null) {
                oldKey = normalize(before.title());
            }
            if (!oldKey.isEmpty()) {
                trie.remove(oldKey, before);
            }
            putLocked(after);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 前缀联想。
     *
     * @param query 用户输入（大小写、连续空白会被归一化）
     * @param limit 最多返回条数，调用方负责夹紧上限
     * @return 按书名字典序排列的条目；无命中返回空列表
     */
    public List<BookSuggestion> suggest(String query, int limit) {
        String key = normalize(query);
        if (key.isEmpty() || limit <= 0) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            return List.copyOf(trie.prefixValues(key, limit));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 当前索引内的书名条数（监控与测试用） */
    public int size() {
        lock.readLock().lock();
        try {
            return trie.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---------- 事务提交后同步 ----------

    /** 只在事务提交成功后写索引；回滚的事务不会留下脏条目 */
    public void syncAdd(BookSuggestion book) {
        afterCommit(() -> add(book));
    }

    public void syncRemove(BookSuggestion book) {
        afterCommit(() -> remove(book));
    }

    public void syncUpdate(BookSuggestion before, BookSuggestion after) {
        afterCommit(() -> update(before, after));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ---------- 内部 ----------

    private void putLocked(BookSuggestion book) {
        String key = normalize(book.title());
        if (key.isEmpty()) {
            return;
        }
        trie.put(key, book);
        keyById.put(book.id(), key);
    }

    /**
     * 归一化：去首尾空白、连续空白压成一个空格、统一小写。
     * 只做这一层，是为了让「Java 编程」与「java  编程」命中同一条，
     * 又不必引入拼音、全半角这类需要外部数据支持的映射（明确不做）。
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.strip();
        StringBuilder sb = new StringBuilder(Math.min(text.length(), MAX_KEY_LENGTH));
        boolean pendingSpace = false;
        for (int i = 0; i < text.length() && sb.length() < MAX_KEY_LENGTH; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = sb.length() > 0;
                continue;
            }
            if (pendingSpace) {
                sb.append(' ');
                pendingSpace = false;
                if (sb.length() >= MAX_KEY_LENGTH) {
                    break;
                }
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
