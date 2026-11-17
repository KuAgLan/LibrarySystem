package com.library.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BizException;
import com.library.common.PageVO;
import com.library.dto.ReaderRequest;
import com.library.entity.Loan;
import com.library.entity.Reader;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** 读者管理 */
@Service
@RequiredArgsConstructor
public class ReaderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReaderMapper readerMapper;
    private final LoanMapper loanMapper;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    /** 新建读者的门户初始密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    public PageVO<Reader> page(String keyword, String type, String status, long pageNo, long pageSize) {
        var wrapper = Wrappers.<Reader>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Reader::getName, kw).or().like(Reader::getCardNo, kw).or().like(Reader::getPhone, kw));
        }
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            wrapper.eq(Reader::getType, type.toUpperCase());
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(Reader::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(Reader::getId);
        IPage<Reader> result = readerMapper.selectPage(
                new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))), wrapper);
        return PageVO.of(result);
    }

    public Reader require(Long id) {
        Reader reader = readerMapper.selectById(id);
        if (reader == null) {
            throw new BizException(404, "读者不存在");
        }
        return reader;
    }

    @Transactional
    public Reader create(ReaderRequest request) {
        Reader reader = new Reader();
        reader.setCardNo(generateCardNo());
        apply(reader, request);
        reader.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        reader.setUnpaidFine(BigDecimal.ZERO);
        reader.setBorrowedNow(0);
        reader.setTotalBorrow(0);
        reader.setDeleted(0);
        readerMapper.insert(reader);
        auditService.log("READER_CREATE", "reader", reader.getId(),
                "登记读者 " + reader.getName() + "（" + reader.getCardNo() + "）");
        return reader;
    }

    @Transactional
    public Reader update(Long id, ReaderRequest request) {
        require(id);
        Reader update = new Reader();
        update.setId(id);
        apply(update, request);
        readerMapper.updateById(update);
        auditService.log("READER_UPDATE", "reader", id, "修改读者 " + update.getName());
        return readerMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        Reader reader = require(id);
        if (reader.getBorrowedNow() != null && reader.getBorrowedNow() > 0) {
            throw new BizException(400, "该读者还有 " + reader.getBorrowedNow() + " 本未归还");
        }
        long active = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery()
                .eq(Loan::getReaderId, id).eq(Loan::getStatus, "BORROWED"));
        if (active > 0) {
            throw new BizException(400, "该读者还有未归还的借阅记录");
        }
        readerMapper.deleteById(id);
        auditService.log("READER_DELETE", "reader", id, "删除读者 " + reader.getName());
    }

    @Transactional
    public Reader setStatus(Long id, String status) {
        require(id);
        String normalized = "SUSPENDED".equalsIgnoreCase(status) ? "SUSPENDED" : "ACTIVE";
        Reader update = new Reader();
        update.setId(id);
        update.setStatus(normalized);
        readerMapper.updateById(update);
        auditService.log("READER_STATUS", "reader", id, "状态变更：" + normalized);
        return readerMapper.selectById(id);
    }

    @Transactional
    public Reader payFine(Long id) {
        Reader reader = require(id);
        BigDecimal amount = reader.getUnpaidFine() == null ? BigDecimal.ZERO : reader.getUnpaidFine();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "该读者没有未缴罚金");
        }
        readerMapper.clearFine(id);
        Long loan = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery()
                .eq(Loan::getReaderId, id).eq(Loan::getFinePaid, 0).gt(Loan::getFineAmount, BigDecimal.ZERO));
        if (loan > 0) {
            loanMapper.update(null, Wrappers.<Loan>lambdaUpdate()
                    .eq(Loan::getReaderId, id).eq(Loan::getFinePaid, 0).set(Loan::getFinePaid, 1));
        }
        auditService.log("READER_FINE_PAY", "reader", id, "缴纳罚金 " + amount.toPlainString() + " 元");
        return readerMapper.selectById(id);
    }

    /** 管理员重置读者门户密码 */
    @Transactional
    public void resetPassword(Long id) {
        Reader reader = require(id);
        Reader update = new Reader();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        readerMapper.updateById(update);
        auditService.log("READER_RESET_PASSWORD", "reader", id,
                "重置 " + reader.getName() + " 的门户密码为默认密码");
    }

    private void apply(Reader target, ReaderRequest request) {
        target.setName(request.name().trim());
        target.setPhone(request.phone() == null ? "" : request.phone().trim());
        target.setEmail(request.email() == null ? "" : request.email().trim());
        target.setType(normalizeType(request.type()));
        target.setDepartment(request.department() == null ? "" : request.department().trim());
        target.setStatus("SUSPENDED".equalsIgnoreCase(request.status()) ? "SUSPENDED" : "ACTIVE");
        target.setRemark(request.remark() == null ? "" : request.remark().trim());
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "PUBLIC";
        }
        return switch (type.trim().toUpperCase()) {
            case "STUDENT" -> "STUDENT";
            case "TEACHER" -> "TEACHER";
            case "PUBLIC" -> "PUBLIC";
            default -> throw new BizException(400, "读者类型不合法：" + type);
        };
    }

    private String generateCardNo() {
        String prefix = "R" + LocalDate.now().format(DAY);
        for (int i = 0; i < 50; i++) {
            String no = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
            if (readerMapper.selectCount(Wrappers.<Reader>lambdaQuery().eq(Reader::getCardNo, no)) == 0) {
                return no;
            }
        }
        return prefix + System.currentTimeMillis() % 100000;
    }
}
