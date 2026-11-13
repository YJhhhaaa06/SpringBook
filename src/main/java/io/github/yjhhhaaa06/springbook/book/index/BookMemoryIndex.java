package io.github.yjhhhaaa06.springbook.book.index;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.common.AfterCommit;
import io.github.yjhhhaaa06.springbook.ds.SeqList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 图书内存索引：一本书一条记录，按 ISBN 有序存放在 {@link SeqList} 里。
 *
 * <p><b>它是什么</b>：MySQL 的派生只读视图。权威数据始终在 {@code books} 表 ——
 * 库存的条件更新、乐观锁、CHECK 约束、行锁都在数据库里完成，索引<b>从不参与写入决策</b>。
 * 它只回答两件事：按 ISBN 取一本书（二分）、按 id 取一本书（目录 + 下标）。
 *
 * <p><b>为什么值得存在</b>：图书馆读多写少，详情页是最热的入口。主键/唯一索引点查虽然已是
 * 微秒级，但走缓存能让读路径不再占用连接池和事务；更重要的是，它让线性表成为主链路的
 * 真实存储，而不是摆设用的样例。
 *
 * <p><b>为什么缓存里含库存</b>：{@code availableCopies} 是本类唯一会变的字段。借书、还书、
 * 入库下架、删除、取消预约、查预约队列（惰性清理）都会改它，因此这六条路径都在事务提交后
 * <b>回查整行覆盖</b>，而不是按差值加减。差值看似更省一次查询，但实际算不出来：
 * 归还一笔借阅的净变化可能是 0（回补 +1 后立刻被 READY 预约占用 -1），
 * 借书也可能先因清理过期预约而 +1、再因扣减而 -1。只有回查能保证与库一致。
 *
 * <p><b>为什么用 {@link HashMap} 做 id 目录</b>：数组按下标访问是 O(1)，但「按 id 找下标」
 * 需要一次映射。它只是目录页，不承担线性表语义 —— 数据只在 {@code SeqList} 里存一份。
 * 插入/删除会让后缀元素的下标整体位移，因此要修复受影响的后缀；这一步与元素搬移同阶，
 * 都是 O(n)，没有改变复杂度量级。
 *
 * <p><b>线程安全</b>：读写锁保护。读多写少，读锁可并发；重建时整段持有写锁，
 * 读请求会短暂阻塞，而不是读到半成品索引。
 */
@Component
public class BookMemoryIndex {

    private static final Logger log = LoggerFactory.getLogger(BookMemoryIndex.class);

    private static final Comparator<String> ISBN_ORDER = Comparator.naturalOrder();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final SeqList<Book, String> books = new SeqList<>(Book::getIsbn, ISBN_ORDER);
    /** id → 下标。只是目录，不是第二份数据 */
    private final Map<Long, Integer> indexById = new HashMap<>();

    /**
     * 全量重建（启动装载、或人工修数据后调用）。
     *
     * <p>先按 ISBN 排序再逐条追加，而不是拿原始顺序逐条有序插入：后者每次都要搬移平均 n/2 个
     * 元素，总代价 O(n²)。排序 O(n log n) + 尾部追加 O(1) + 一次性建目录 O(n)，
     * 数据量上万时差距是几十毫秒与几秒的区别。
     */
    public void rebuild(List<Book> source) {
        List<Book> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparing(Book::getIsbn));

        int loaded;
        lock.writeLock().lock();
        try {
            books.clear();
            indexById.clear();
            for (Book book : ordered) {
                books.add(book);
            }
            int size = books.size();
            for (int i = 0; i < size; i++) {
                indexById.put(books.get(i).getId(), i);
            }
            loaded = size;
        } finally {
            lock.writeLock().unlock();
        }
        log.info("图书内存索引重建完成，共 {} 条（按 ISBN 有序）", loaded);
    }

    public void add(Book book) {
        lock.writeLock().lock();
        try {
            putLocked(book);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 整行覆盖。{@code after} 必须是数据库提交后的完整行（含最新的 availableCopies）。
     *
     * <p>ISBN 在业务上不可修改（{@code updateTitleAndPrice} 只动书名和价格），因此常规路径
     * 是同位置替换；万一 ISBN 变了，退化为「摘旧 + 挂新」，靠重新二分定位，不会留下错位条目。
     */
    public void update(Book after) {
        lock.writeLock().lock();
        try {
            Integer position = indexById.get(after.getId());
            if (position == null) {
                // 索引里没有这条（装载窗口、或已被移除）：按新增处理，保证后续能读到
                putLocked(after);
                return;
            }
            Book before = books.get(position);
            if (Objects.equals(before.getIsbn(), after.getIsbn())) {
                books.set(position, after);
                return;
            }
            books.removeAt(position);
            indexById.remove(before.getId());
            repairIndexFrom(position);
            putLocked(after);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(Long id) {
        if (id == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            Integer position = indexById.remove(id);
            if (position == null) {
                return;
            }
            books.removeAt(position);
            repairIndexFrom(position);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Book findById(Long id) {
        if (id == null) {
            return null;
        }
        lock.readLock().lock();
        try {
            Integer position = indexById.get(id);
            return position == null ? null : books.get(position);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 按 ISBN 二分定位。未命中返回 null，调用方决定回源还是报 404。 */
    public Book findByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        lock.readLock().lock();
        try {
            int position = books.indexOf(isbn);
            return position < 0 ? null : books.get(position);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 批量按 id 取书，保持传入顺序、跳过不存在的 id。
     * 供热门榜把「先聚合出 id、再回表取书名」那一步挪到内存里。
     */
    public List<Book> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<Book> found = new ArrayList<>(ids.size());
            for (Long id : ids) {
                Integer position = id == null ? null : indexById.get(id);
                if (position != null) {
                    found.add(books.get(position));
                }
            }
            return found;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 当前索引内的图书条数（监控与测试用） */
    public int size() {
        lock.readLock().lock();
        try {
            return books.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---------- 事务提交后同步 ----------

    public void syncAdd(Book book) {
        AfterCommit.run(() -> add(book));
    }

    public void syncUpdate(Book after) {
        AfterCommit.run(() -> update(after));
    }

    public void syncRemove(Long id) {
        AfterCommit.run(() -> remove(id));
    }

    // ---------- 内部 ----------

    private void putLocked(Book book) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(book.getId(), "book.id");
        Objects.requireNonNull(book.getIsbn(), "book.isbn");
        int position = books.add(book);
        repairIndexFrom(position);
    }

    /** 从 from 开始把「id → 下标」修正到与数组一致；新元素也会被写进去 */
    private void repairIndexFrom(int from) {
        int size = books.size();
        for (int i = from; i < size; i++) {
            indexById.put(books.get(i).getId(), i);
        }
    }
}
