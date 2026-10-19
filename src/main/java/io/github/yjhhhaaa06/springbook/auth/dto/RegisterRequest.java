package io.github.yjhhhaaa06.springbook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自助注册请求：创建读者资料 + READER 账号，成功即签发 token。
 * 密码强度从简（仅长度），与第一版「最小实现」口径一致。
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度 3-50 字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 50, message = "密码长度 6-50 字符")
        String password,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名最长 50 字符")
        String name,

        @NotBlank(message = "手机号不能为空")
        @Size(max = 20, message = "手机号最长 20 字符")
        String phone
) {
}
