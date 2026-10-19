package io.github.yjhhhaaa06.springbook.reader.mapper;

import io.github.yjhhhaaa06.springbook.reader.domain.Reader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReaderMapper {

    int insert(Reader reader);

    Reader findById(@Param("id") Long id);

    Reader findByPhone(@Param("phone") String phone);
}
