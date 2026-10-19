package io.github.yjhhhaaa06.springbook.reader.service;

import io.github.yjhhhaaa06.springbook.auth.AccessPolicy;
import io.github.yjhhhaaa06.springbook.auth.AuthPrincipal;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.common.PageResult;
import io.github.yjhhhaaa06.springbook.loan.domain.Loan;
import io.github.yjhhhaaa06.springbook.loan.mapper.LoanMapper;
import io.github.yjhhhaaa06.springbook.reader.domain.Reader;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderCreateRequest;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderDetailResponse;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderResponse;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderStatusRequest;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderUpdateRequest;
import io.github.yjhhhaaa06.springbook.reader.mapper.ReaderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅人业务：新增（手机号唯一）、详情查询（含在借列表与未缴罚款汇总，数据归属见 AccessPolicy）、
 * 管理端分页/资料修改/手动冻结解冻（接口层 ADMIN 限制）。
 */
@Service
public class ReaderService {

    private final ReaderMapper readerMapper;
    private final LoanMapper loanMapper;
    private final AccessPolicy accessPolicy;

    public ReaderService(ReaderMapper readerMapper, LoanMapper loanMapper, AccessPolicy accessPolicy) {
        this.readerMapper = readerMapper;
        this.loanMapper = loanMapper;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public ReaderResponse create(ReaderCreateRequest req) {
        if (readerMapper.findByPhone(req.phone()) != null) {
            throw new BizException(ErrorCode.READER_PHONE_DUPLICATED);
        }
        Reader reader = new Reader();
        reader.setName(req.name());
        reader.setPhone(req.phone());
        try {
            readerMapper.insert(reader);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.READER_PHONE_DUPLICATED);
        }
        return ReaderResponse.from(readerMapper.findById(reader.getId()));
    }

    public ReaderDetailResponse getDetail(Long id, AuthPrincipal principal) {
        accessPolicy.assertSelfOrAdmin(principal, id);
        Reader reader = readerMapper.findById(id);
        if (reader == null) {
            throw new BizException(ErrorCode.READER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ReaderDetailResponse.OnLoanItem> onLoans = loanMapper.listBorrowedByReader(id)
                .stream()
                .map(loan -> new ReaderDetailResponse.OnLoanItem(
                        loan.getId(), loan.getBookId(), loan.getBookTitle(),
                        loan.getBorrowedAt(), loan.getDueAt(), loan.getDueAt().isBefore(now)))
                .toList();
        return ReaderDetailResponse.of(reader, loanMapper.sumUnpaidByReader(id), onLoans);
    }

    public PageResult<ReaderResponse> list(String keyword, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = readerMapper.countByKeyword(kw);
        List<ReaderResponse> records = readerMapper.listByKeyword(kw, (p - 1) * s, s)
                .stream()
                .map(ReaderResponse::from)
                .toList();
        return PageResult.of(total, p, s, records);
    }

    @Transactional
    public ReaderResponse update(Long id, ReaderUpdateRequest req) {
        Reader reader = readerMapper.findById(id);
        if (reader == null) {
            throw new BizException(ErrorCode.READER_NOT_FOUND);
        }
        Reader byPhone = readerMapper.findByPhone(req.phone());
        if (byPhone != null && !byPhone.getId().equals(id)) {
            throw new BizException(ErrorCode.READER_PHONE_DUPLICATED);
        }
        try {
            readerMapper.update(id, req.name(), req.phone());
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.READER_PHONE_DUPLICATED);
        }
        return ReaderResponse.from(readerMapper.findById(id));
    }

    @Transactional
    public ReaderResponse updateStatus(Long id, ReaderStatusRequest req) {
        Reader reader = readerMapper.findById(id);
        if (reader == null) {
            throw new BizException(ErrorCode.READER_NOT_FOUND);
        }
        readerMapper.updateStatus(id, req.status());
        return ReaderResponse.from(readerMapper.findById(id));
    }
}
