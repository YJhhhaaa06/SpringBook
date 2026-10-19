package io.github.yjhhhaaa06.springbook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求（全角色，修改自己的密码）。
 */
public record ChangePasswordRequest(

        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 50, message = "密码长度 6-50 字符")
        String newPassword
) {
}
