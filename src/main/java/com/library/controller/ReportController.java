package com.library.controller;

import com.library.common.R;
import com.library.service.ReportService;
import com.library.vo.LoanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(reportService.dashboard());
    }

    @GetMapping("/overdue")
    public R<List<LoanVO>> overdue(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(reportService.overdueList(limit));
    }

    @GetMapping("/loans/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status) {
        byte[] body = reportService.exportLoansCsv(status).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"loans.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
