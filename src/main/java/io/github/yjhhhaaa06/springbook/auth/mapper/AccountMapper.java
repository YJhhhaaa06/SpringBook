package io.github.yjhhhaaa06.springbook.auth.mapper;

import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    Account findByUsername(@Param("username") String username);

    int insert(Account account);

    long countByRole(@Param("role") String role);
}
