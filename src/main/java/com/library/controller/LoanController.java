package com.library.controller;

import com.library.common.PageVO;
import com.library.common.R;
import com.library.dto.BorrowRequest;
import com.library.entity.Loan;
import com.library.service.LoanService;
import com.library.vo.LoanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
public class LoanController {

    private final LoanService loanService;

    @GetMapping
    public R<PageVO<LoanVO>> page(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Long readerId,
                                  @RequestParam(defaultValue = "1") long page,
                                  @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(loanService.page(status, keyword, readerId, page, pageSize));
    }

    @GetMapping("/overdue")
    public R<List<LoanVO>> overdue(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(loanService.overdue(limit));
    }

    @PostMapping("/borrow")
    public R<Loan> borrow(@Valid @RequestBody BorrowRequest request) {
        return R.ok(loanService.borrow(request));
    }

    @PostMapping("/{id}/return")
    public R<Loan> returnBook(@PathVariable Long id) {
        return R.ok(loanService.returnBook(id));
    }

    @PostMapping("/{id}/renew")
    public R<Loan> renew(@PathVariable Long id) {
        return R.ok(loanService.renew(id));
    }
}
