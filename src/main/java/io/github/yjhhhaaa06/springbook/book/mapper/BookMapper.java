package io.github.yjhhhaaa06.springbook.book.mapper;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图书表访问。XML 实现见 classpath:mapper/BookMapper.xml。
 */
@Mapper
public interface BookMapper {

    int insert(Book book);

    Book findById(@Param("id") Long id);

    /** 行锁读取：借书 / 还书 / 预约流转等需要串行化库存与队列操作的场景使用 */
    Book lockById(@Param("id") Long id);

    Book findByIsbn(@Param("isbn") String isbn);

    /** 全量读取（仅书名索引启动装载使用；其余查询一律走条件检索） */
    List<Book> listAll();

    /** 按 id 批量读取（热门榜拿到 top-K 后补齐书名，避免逐条查询）；调用方保证 ids 非空 */
    List<Book> findByIds(@Param("ids") List<Long> ids);

    /** 乐观锁更新书名与价格，返回受影响行数（0 表示版本冲突） */
    int updateTitleAndPrice(@Param("id") Long id,
                            @Param("title") String title,
                            @Param("priceCents") Integer priceCents,
                            @Param("expectedVersion") Integer expectedVersion);

    int deleteById(@Param("id") Long id);

    long countByKeyword(@Param("keyword") String keyword);

    List<Book> listByKeyword(@Param("keyword") String keyword,
                             @Param("offset") int offset,
                             @Param("size") int size);

    /** 条件扣减库存：available_copies &gt; 0 才生效，返回受影响行数 */
    int decrementIfAvailable(@Param("id") Long id);

    /** 条件回补库存：available_copies &lt; total_copies 才生效，返回受影响行数 */
    int incrementIfBelowTotal(@Param("id") Long id);

    /** 入库/下架：调整总册数并同步增减可借数（行锁内调用，expectedTotal 防御并发），返回受影响行数 */
    int updateCopies(@Param("id") Long id,
                     @Param("totalCopies") Integer totalCopies,
                     @Param("delta") Integer delta,
                     @Param("expectedTotal") Integer expectedTotal);
}
