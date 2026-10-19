package io.github.yjhhhaaa06.springbook.reader.controller;

import io.github.yjhhhaaa06.springbook.common.Result;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderCreateRequest;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderDetailResponse;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderResponse;
import io.github.yjhhhaaa06.springbook.reader.service.ReaderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读者接口。新增读者要求 ADMIN，查询要求已认证。
 */
@RestController
@RequestMapping("/api/readers")
public class ReaderController {

    private final ReaderService readerService;

    public ReaderController(ReaderService readerService) {
        this.readerService = readerService;
    }

    @PostMapping
    public Result<ReaderResponse> create(@Valid @RequestBody ReaderCreateRequest request) {
        return Result.ok(readerService.create(request));
    }

    @GetMapping("/{id}")
    public Result<ReaderDetailResponse> get(@PathVariable Long id) {
        return Result.ok(readerService.getDetail(id));
    }
}
