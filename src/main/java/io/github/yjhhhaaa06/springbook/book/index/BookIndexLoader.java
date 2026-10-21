package io.github.yjhhhaaa06.springbook.book.index;

import io.github.yjhhhaaa06.springbook.book.dto.BookSuggestion;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把 books 全量灌进 {@link BookIndex}。
 *
 * <p>索引是派生数据，因此选「启动重建」而不是「持久化索引」：库重启、索引文件损坏、
 * 直接改库表，都不会产生不一致 —— 下一次启动自动对齐。数据量在万级以内，
 * 一次全表扫描的代价远小于维护增量一致性的复杂度。
 */
@Component
public class BookIndexLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookIndexLoader.class);

    private final BookMapper bookMapper;
    private final BookIndex bookIndex;

    public BookIndexLoader(BookMapper bookMapper, BookIndex bookIndex) {
        this.bookMapper = bookMapper;
        this.bookIndex = bookIndex;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<BookSuggestion> books = bookMapper.listAll().stream()
                .map(BookSuggestion::from)
                .toList();
        bookIndex.rebuild(books);
        log.info("书名索引装载 {} 条，联想接口可用", books.size());
    }
}
