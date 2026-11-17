package com.library.controller;

import com.library.common.PageVO;
import com.library.common.R;
import com.library.config.AuthUser;
import com.library.entity.Loan;
import com.library.entity.Reservation;
import com.library.service.AuditService;
import com.library.service.ReaderPortalService;
import com.library.vo.LoanVO;
import com.library.vo.ReaderBookVO;
import com.library.vo.ReaderProfileVO;
import com.library.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 读者自助门户：只允许 READER 身份，数据范围限本人 */
@RestController
@RequestMapping("/reader")
@RequiredArgsConstructor
@PreAuthorize("hasRole('READER')")
public class ReaderPortalController {

    private final ReaderPortalService portalService;
    private final AuditService auditService;

    @GetMapping("/profile")
    public R<ReaderProfileVO> profile() {
        return R.ok(portalService.profile(current()));
    }

    @GetMapping("/books")
    public R<PageVO<ReaderBookVO>> books(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(defaultValue = "false") boolean onlyAvailable,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "12") long pageSize) {
        return R.ok(portalService.search(keyword, category, page, pageSize, onlyAvailable));
    }

    @GetMapping("/loans")
    public R<PageVO<LoanVO>> loans(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(portalService.myLoans(current(), status, page, pageSize));
    }

    @PostMapping("/loans/borrow")
    public R<Loan> borrow(@RequestBody Map<String, Object> body) {
        Object raw = body.get("bookId");
        if (raw == null) {
            throw new com.library.common.BizException(400, "请选择图书");
        }
        Long bookId = Long.valueOf(String.valueOf(raw));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(portalService.borrow(current(), bookId, remark));
    }

    @PostMapping("/loans/{id}/renew")
    public R<Loan> renew(@PathVariable Long id) {
        return R.ok(portalService.renew(current(), id));
    }

    @PostMapping("/books/{id}/reserve")
    public R<Reservation> reserve(@PathVariable Long id) {
        return R.ok(portalService.reserve(current(), id));
    }

    @GetMapping("/reservations")
    public R<List<ReservationVO>> reservations() {
        return R.ok(portalService.myReservations(current()));
    }

    @PostMapping("/reservations/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        portalService.cancelReservation(current(), id);
        return R.success();
    }

    @GetMapping("/fine")
    public R<Map<String, Object>> fine() {
        BigDecimal fine = portalService.myFine(current());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unpaidFine", fine);
        data.put("payAtDesk", true);
        data.put("tip", fine.compareTo(BigDecimal.ZERO) > 0 ? "请到服务台缴纳罚金后恢复借阅" : "暂无未缴罚金");
        return R.ok(data);
    }

    private AuthUser current() {
        AuthUser user = auditService.current();
        if (user == null) {
            throw new com.library.common.BizException(401, "登录状态已失效");
        }
        return user;
    }
}