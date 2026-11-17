package com.library.controller;

import com.library.common.PageVO;
import com.library.common.R;
import com.library.dto.BookRequest;
import com.library.entity.Book;
import com.library.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping
    public R<PageVO<Book>> page(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "1") long page,
                                @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(bookService.page(keyword, category, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public R<Book> detail(@PathVariable Long id) {
        return R.ok(bookService.require(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public R<Book> create(@Valid @RequestBody BookRequest request) {
        return R.ok(bookService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public R<Book> update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return R.ok(bookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return R.success();
    }
}
