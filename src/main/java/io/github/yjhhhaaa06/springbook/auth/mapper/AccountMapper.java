package io.github.yjhhhaaa06.springbook.auth.mapper;

import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    Account findById(@Param("id") Long id);

    Account findByUsername(@Param("username") String username);

    int insert(Account account);

    long countByRole(@Param("role") String role);

    /** 更新口令散列（修改密码） */
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
