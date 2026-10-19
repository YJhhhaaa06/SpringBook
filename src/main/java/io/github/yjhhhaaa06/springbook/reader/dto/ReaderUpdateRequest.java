package io.github.yjhhhaaa06.springbook.reader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改读者资料请求。
 */
public record ReaderUpdateRequest(

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名最长 50 字符")
        String name,

        @NotBlank(message = "手机号不能为空")
        @Size(max = 20, message = "手机号最长 20 字符")
        String phone
) {
}
