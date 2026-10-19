package io.github.yjhhhaaa06.springbook.reader.mapper;

import io.github.yjhhhaaa06.springbook.reader.domain.Reader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 借阅人表访问。XML 实现见 classpath:mapper/ReaderMapper.xml。
 */
@Mapper
public interface ReaderMapper {

    int insert(Reader reader);

    Reader findById(@Param("id") Long id);

    Reader findByPhone(@Param("phone") String phone);

    /** 行锁读取：借出前锁定读者，串行化同一读者的并发借阅（上限/罚款校验不被竞态穿透） */
    Reader lockById(@Param("id") Long id);

    /** 更新状态（自动冻结/解冻与管理员手动冻结/解冻共用） */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 修改读者资料（姓名/手机号） */
    int update(@Param("id") Long id, @Param("name") String name, @Param("phone") String phone);

    /** 管理端分页：keyword 按姓名/手机号模糊匹配，可为 null */
    long countByKeyword(@Param("keyword") String keyword);

    List<Reader> listByKeyword(@Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("size") int size);
}
