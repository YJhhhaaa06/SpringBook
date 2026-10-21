package io.github.yjhhhaaa06.springbook.common;

import lombok.Data;

/**
 * 「按图书聚合的计数」的读模型：`bookId` → `hits`。
 *
 * <p>放在 common 而不是某个模块的 dto：借阅次数来自 loan 模块、预约人数来自
 * reservation 模块，热门榜在 book 模块里合并两者。这是一个跨模块的投影，
 * 不属于任何单一模块的领域模型。
 *
 * <p>字段用可变类而不是 record：MyBatis 的结果映射走 setter，record 需要
 * 构造器映射配置，为一个两字段的投影不值得。
 */
@Data
public class BookHitCount {

    private Long bookId;

    private Integer hits;
}
