package io.github.yjhhhaaa06.springbook.book.index;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.dto.BookSuggestion;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时用一次全表扫描，把 {@code books} 同时灌进两个派生视图：
 * 按 ISBN 有序的线性表 {@link BookMemoryIndex}，以及书名前缀树 {@link BookIndex}。
 *
 * <p>索引是派生数据，因此选「启动重建」而不是「持久化索引」：库重启、索引文件损坏、
 * 直接改库表，都不会产生不一致 —— 下一次启动自动对齐。数据量在万级以内，
 * 一次全表扫描的代价远小于维护增量一致性的复杂度。
 *
 * <p>两个视图共用同一份 {@code List<Book>}，避免各查一遍全表：它们的权威来源相同、
 * 装载时机也相同，没有理由读两次。
 */
@Component
public class BookIndexLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookIndexLoader.class);

    private final BookMapper bookMapper;
    private final BookMemoryIndex bookMemoryIndex;
    private final BookIndex bookIndex;

    public BookIndexLoader(BookMapper bookMapper, BookMemoryIndex bookMemoryIndex, BookIndex bookIndex) {
        this.bookMapper = bookMapper;
        this.bookMemoryIndex = bookMemoryIndex;
        this.bookIndex = bookIndex;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Book> books = bookMapper.listAll();
        bookMemoryIndex.rebuild(books);
        bookIndex.rebuild(books.stream().map(BookSuggestion::from).toList());
        log.info("图书索引装载 {} 条：内存线性表与书名前缀树均可用", books.size());
    }
}
