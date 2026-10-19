package io.github.yjhhhaaa06.springbook.reader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 手动冻结/解冻请求。
 */
public record ReaderStatusRequest(

        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "ACTIVE|FROZEN", message = "状态仅允许 ACTIVE / FROZEN")
        String status
) {
}
