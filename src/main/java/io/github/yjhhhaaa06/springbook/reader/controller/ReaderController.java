package io.github.yjhhhaaa06.springbook.reader.controller;

import io.github.yjhhhaaa06.springbook.auth.AuthPrincipal;
import io.github.yjhhhaaa06.springbook.common.PageResult;
import io.github.yjhhhaaa06.springbook.common.Result;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderCreateRequest;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderDetailResponse;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderResponse;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderStatusRequest;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderUpdateRequest;
import io.github.yjhhhaaa06.springbook.reader.service.ReaderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读者接口。新增/分页列表/资料修改/冻结解冻要求 ADMIN（SecurityConfig 控制）；
 * 读者详情要求已认证且 READER 仅能查看自己（AccessPolicy 控制）。
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

    @GetMapping
    public Result<PageResult<ReaderResponse>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return Result.ok(readerService.list(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<ReaderDetailResponse> get(@PathVariable Long id,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(readerService.getDetail(id, principal));
    }

    @PutMapping("/{id}")
    public Result<ReaderResponse> update(@PathVariable Long id,
                                         @Valid @RequestBody ReaderUpdateRequest request) {
        return Result.ok(readerService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public Result<ReaderResponse> updateStatus(@PathVariable Long id,
                                               @Valid @RequestBody ReaderStatusRequest request) {
        return Result.ok(readerService.updateStatus(id, request));
    }
}
