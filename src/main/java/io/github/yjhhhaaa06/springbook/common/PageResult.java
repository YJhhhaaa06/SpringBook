package io.github.yjhhhaaa06.springbook.common;

import java.util.List;

/**
 * 分页结果。
 */
public record PageResult<T>(long total, int page, int size, List<T> records) {

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return new PageResult<>(total, page, size, records);
    }
}
