package io.github.yjhhhaaa06.springbook.reader.service;

import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.loan.domain.Loan;
import io.github.yjhhhaaa06.springbook.loan.mapper.LoanMapper;
import io.github.yjhhhaaa06.springbook.reader.domain.Reader;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderCreateRequest;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderDetailResponse;
import io.github.yjhhhaaa06.springbook.reader.dto.ReaderResponse;
import io.github.yjhhhaaa06.springbook.reader.mapper.ReaderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅人业务：新增（手机号唯一）与详情查询（含在借列表）。
 */
@Service
public class ReaderService {

    private final ReaderMapper readerMapper;
    private final LoanMapper loanMapper;

    public ReaderService(ReaderMapper readerMapper, LoanMapper loanMapper) {
        this.readerMapper = readerMapper;
        this.loanMapper = loanMapper;
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

    public ReaderDetailResponse getDetail(Long id) {
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
        return ReaderDetailResponse.of(reader, onLoans);
    }
}
