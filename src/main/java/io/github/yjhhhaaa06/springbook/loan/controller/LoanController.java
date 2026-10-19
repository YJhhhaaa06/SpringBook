package io.github.yjhhhaaa06.springbook.loan.controller;

import io.github.yjhhhaaa06.springbook.auth.AuthPrincipal;
import io.github.yjhhhaaa06.springbook.common.Result;
import io.github.yjhhhaaa06.springbook.loan.dto.LoanCreateRequest;
import io.github.yjhhhaaa06.springbook.loan.dto.LoanResponse;
import io.github.yjhhhaaa06.springbook.loan.dto.LoanReturnResponse;
import io.github.yjhhhaaa06.springbook.loan.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 借还接口：借书、还书（返回罚款金额）、借阅记录查询（均要求已认证，数据归属见 AccessPolicy）；
 * 缴纳罚款（ADMIN，由 SecurityConfig 按路径控制）。
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public Result<LoanResponse> borrow(@Valid @RequestBody LoanCreateRequest request,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(loanService.borrow(request, principal));
    }

    @PostMapping("/{id}/return")
    public Result<LoanReturnResponse> returnBook(@PathVariable Long id,
                                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(loanService.returnBook(id, principal));
    }

    @PostMapping("/{id}/fine/pay")
    public Result<LoanResponse> payFine(@PathVariable Long id) {
        return Result.ok(loanService.payFine(id));
    }

    @GetMapping
    public Result<List<LoanResponse>> list(@RequestParam(required = false) Long readerId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Boolean overdue,
                                           @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(loanService.list(readerId, status, overdue, principal));
    }
}
