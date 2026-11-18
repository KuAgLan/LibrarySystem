package com.library.concurrency;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.dto.BorrowRequest;
import com.library.entity.Book;
import com.library.entity.Reader;
import com.library.mapper.BookMapper;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import com.library.service.LoanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发抢书集成测试：10 个线程同时借最后 1 册，只能有 1 个成功、库存不为负。
 * 需要可用的 MySQL（CI 中通过 service 容器提供）。
 */
@SpringBootTest
class BookStockConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired LoanService loanService;
    @Autowired BookMapper bookMapper;
    @Autowired ReaderMapper readerMapper;
    @Autowired LoanMapper loanMapper;

    @Test
    @DisplayName("10 线程抢最后一册：仅 1 个成功，库存不为负")
    void onlyOneThreadWinsTheLastCopy() throws Exception {
        Book book = new Book();
        book.setIsbn("IT-" + System.nanoTime());
        book.setTitle("并发测试图书");
        book.setAuthor("test");
        book.setCategory("计算机");
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        book.setPrice(BigDecimal.ZERO);
        book.setStatus("ACTIVE");
        book.setDeleted(0);
        bookMapper.insert(book);

        List<Reader> readers = new ArrayList<>();
        List<Long> readerIds = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                Reader r = new Reader();
                r.setCardNo("IT" + System.nanoTime() + i);
                r.setName("并发读者" + i);
                r.setType("TEACHER");
                r.setStatus("ACTIVE");
                r.setUnpaidFine(BigDecimal.ZERO);
                r.setBorrowedNow(0);
                r.setTotalBorrow(0);
                r.setDeleted(0);
                readerMapper.insert(r);
                readers.add(r);
                readerIds.add(r.getId());
            }

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();

            for (Reader r : readers) {
                pool.submit(() -> {
                    try {
                        start.await();
                        loanService.borrow(new BorrowRequest(book.getId(), r.getId(), "并发测试"));
                        success.incrementAndGet();
                    } catch (Exception e) {
                        rejected.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            Book after = bookMapper.selectById(book.getId());
            assertThat(success.get()).as("成功借出的线程数").isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(THREADS - 1);
            assertThat(after.getAvailableCopies()).isZero();
            assertThat(after.getAvailableCopies()).isGreaterThanOrEqualTo(0);
        } finally {
            // 清理：归还 + 删除测试数据
            loanMapper.selectList(Wrappers.<com.library.entity.Loan>lambdaQuery()
                            .eq(com.library.entity.Loan::getBookId, book.getId()))
                    .forEach(l -> loanMapper.deleteById(l.getId()));
            readerIds.forEach(readerMapper::deleteById);
            bookMapper.deleteById(book.getId());
        }
    }
}
