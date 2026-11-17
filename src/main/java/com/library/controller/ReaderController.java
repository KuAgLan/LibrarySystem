package com.library.controller;

import com.library.common.PageVO;
import com.library.common.R;
import com.library.dto.ReaderRequest;
import com.library.entity.Reader;
import com.library.service.ReaderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/readers")
@RequiredArgsConstructor
public class ReaderController {

    private final ReaderService readerService;

    @GetMapping
    public R<PageVO<Reader>> page(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "1") long page,
                                  @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(readerService.page(keyword, type, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public R<Reader> detail(@PathVariable Long id) {
        return R.ok(readerService.require(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public R<Reader> create(@Valid @RequestBody ReaderRequest request) {
        return R.ok(readerService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public R<Reader> update(@PathVariable Long id, @Valid @RequestBody ReaderRequest request) {
        return R.ok(readerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        readerService.delete(id);
        return R.success();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public R<Reader> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(readerService.setStatus(id, body.get("status")));
    }

    @PostMapping("/{id}/pay-fine")
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public R<Reader> payFine(@PathVariable Long id) {
        return R.ok(readerService.payFine(id));
    }
}
