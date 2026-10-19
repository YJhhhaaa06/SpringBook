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
}
