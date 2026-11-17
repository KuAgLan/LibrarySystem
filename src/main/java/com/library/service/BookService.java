package com.library.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BizException;
import com.library.common.PageVO;
import com.library.dto.BookRequest;
import com.library.entity.Book;
import com.library.entity.Loan;
import com.library.mapper.BookMapper;
import com.library.mapper.LoanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 图书管理 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookMapper bookMapper;
    private final LoanMapper loanMapper;
    private final AuditService auditService;

    public PageVO<Book> page(String keyword, String category, String status, long pageNo, long pageSize) {
        var wrapper = Wrappers.<Book>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Book::getTitle, kw).or().like(Book::getAuthor, kw).or().like(Book::getIsbn, kw));
        }
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            wrapper.eq(Book::getCategory, category);
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(Book::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(Book::getId);
        IPage<Book> result = bookMapper.selectPage(
                new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))), wrapper);
        return PageVO.of(result);
    }

    public Book require(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BizException(404, "图书不存在");
        }
        return book;
    }

    @Transactional
    public Book create(BookRequest request) {
        if (bookMapper.selectCount(Wrappers.<Book>lambdaQuery().eq(Book::getIsbn, request.isbn().trim())) > 0) {
            throw new BizException(409, "ISBN 已存在");
        }
        int total = Math.max(1, request.totalCopies() == null ? 1 : request.totalCopies());
        Book book = new Book();
        book.setIsbn(request.isbn().trim());
        book.setTitle(request.title().trim());
        book.setAuthor(nvl(request.author()));
        book.setPublisher(nvl(request.publisher()));
        book.setCategory(nvl(request.category()).isEmpty() ? "综合" : request.category().trim());
        book.setLocation(nvl(request.location()));
        book.setTotalCopies(total);
        book.setAvailableCopies(total);
        book.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        book.setPublishYear(request.publishYear());
        book.setStatus("OFF_SHELF".equalsIgnoreCase(request.status()) ? "OFF_SHELF" : "ACTIVE");
        book.setRemark(nvl(request.remark()));
        book.setDeleted(0);
        bookMapper.insert(book);
        auditService.log("BOOK_CREATE", "book", book.getId(), "新增图书《" + book.getTitle() + "》");
        return book;
    }

    @Transactional
    public Book update(Long id, BookRequest request) {
        Book book = require(id);
        if (bookMapper.selectCount(Wrappers.<Book>lambdaQuery()
                .eq(Book::getIsbn, request.isbn().trim()).ne(Book::getId, id)) > 0) {
            throw new BizException(409, "ISBN 已被其它图书使用");
        }
        int borrowed = book.getTotalCopies() - book.getAvailableCopies();
        int total = Math.max(1, request.totalCopies() == null ? book.getTotalCopies() : request.totalCopies());
        if (total < borrowed) {
            throw new BizException(400, "馆藏总数不能小于已借出数量（" + borrowed + " 本）");
        }
        Book update = new Book();
        update.setId(id);
        update.setIsbn(request.isbn().trim());
        update.setTitle(request.title().trim());
        update.setAuthor(nvl(request.author()));
        update.setPublisher(nvl(request.publisher()));
        update.setCategory(nvl(request.category()).isEmpty() ? "综合" : request.category().trim());
        update.setLocation(nvl(request.location()));
        update.setTotalCopies(total);
        update.setAvailableCopies(total - borrowed);
        update.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        update.setPublishYear(request.publishYear());
        if (request.status() != null) {
            update.setStatus("OFF_SHELF".equalsIgnoreCase(request.status()) ? "OFF_SHELF" : "ACTIVE");
        }
        update.setRemark(nvl(request.remark()));
        bookMapper.updateById(update);
        auditService.log("BOOK_UPDATE", "book", id, "修改图书《" + update.getTitle() + "》");
        return bookMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        Book book = require(id);
        long active = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery()
                .eq(Loan::getBookId, id).eq(Loan::getStatus, "BORROWED"));
        if (active > 0) {
            throw new BizException(400, "该图书还有 " + active + " 本在借，不能删除");
        }
        bookMapper.deleteById(id);
        auditService.log("BOOK_DELETE", "book", id, "删除图书《" + book.getTitle() + "》");
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
