package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.entity.Book;
import com.library.entity.Loan;
import com.library.entity.Reader;
import com.library.entity.SysUser;
import com.library.mapper.BookMapper;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import com.library.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/** 初始化：账号、图书、读者与演示借阅（均为虚构数据） */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final SettingService settingService;
    private final SysUserMapper userMapper;
    private final BookMapper bookMapper;
    private final ReaderMapper readerMapper;
    private final LoanMapper loanMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo-data:true}")
    private boolean seedDemo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        settingService.ensureDefaults();
        seedUsers();
        if (seedDemo && bookMapper.selectCount(null) == 0) {
            seedBooks();
            seedReaders();
            seedLoans();
        }
        log.info("初始化完成：账号 {} 个，图书 {} 种，读者 {} 位，借阅 {} 条",
                userMapper.selectCount(null), bookMapper.selectCount(null),
                readerMapper.selectCount(null), loanMapper.selectCount(null));
    }

    private void seedUsers() {
        createUser("admin", "admin123", "系统管理员", "ADMIN");
        createUser("librarian", "lib123456", "馆员A", "LIBRARIAN");
    }

    private void createUser(String username, String password, String name, String role) {
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(role);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
    }

    private void seedBooks() {
        record B(String isbn, String title, String author, String publisher, String category, int copies, String price, int year) {
        }
        List<B> books = List.of(
                new B("9787111213826", "深入理解计算机系统", "Randal E. Bryant", "机械工业出版社", "计算机", 5, "139.00", 2016),
                new B("9787111544937", "Java 编程思想", "Bruce Eckel", "机械工业出版社", "计算机", 4, "108.00", 2007),
                new B("9787115546081", "Spring 实战（第 6 版）", "Craig Walls", "人民邮电出版社", "计算机", 3, "119.00", 2022),
                new B("9787506365437", "活着", "余华", "作家出版社", "文学", 6, "45.00", 2012),
                new B("9787020024759", "红楼梦", "曹雪芹", "人民文学出版社", "文学", 8, "59.70", 1996),
                new B("9787544270878", "百年孤独", "加西亚·马尔克斯", "南海出版公司", "文学", 3, "55.00", 2011),
                new B("9787508647357", "人类简史", "尤瓦尔·赫拉利", "中信出版社", "历史", 4, "68.00", 2014),
                new B("9787108009821", "万历十五年", "黄仁宇", "生活·读书·新知三联书店", "历史", 3, "28.00", 1997),
                new B("9787115373991", "算法（第 4 版）", "Robert Sedgewick", "人民邮电出版社", "计算机", 4, "99.00", 2012),
                new B("9787543223753", "国富论", "亚当·斯密", "上海三联书店", "经济", 2, "88.00", 2009),
                new B("9787020104345", "围城", "钱钟书", "人民文学出版社", "文学", 5, "39.00", 2014),
                new B("9787111407010", "重构：改善既有代码的设计", "Martin Fowler", "人民邮电出版社", "计算机", 3, "89.00", 2019)
        );
        for (B b : books) {
            Book book = new Book();
            book.setIsbn(b.isbn());
            book.setTitle(b.title());
            book.setAuthor(b.author());
            book.setPublisher(b.publisher());
            book.setCategory(b.category());
            book.setLocation("A 区-" + (100 + bookMapper.selectCount(null)));
            book.setTotalCopies(b.copies());
            book.setAvailableCopies(b.copies());
            book.setPrice(new BigDecimal(b.price()));
            book.setPublishYear(b.year());
            book.setStatus("ACTIVE");
            book.setRemark("");
            book.setDeleted(0);
            bookMapper.insert(book);
        }
    }

    private void seedReaders() {
        record R(String name, String phone, String type, String dept) {
        }
        List<R> readers = List.of(
                new R("读者001", "13800000001", "STUDENT", "计算机学院"),
                new R("读者002", "13800000002", "STUDENT", "文学院"),
                new R("读者003", "13800000003", "TEACHER", "计算机学院"),
                new R("读者004", "13800000004", "TEACHER", "历史学院"),
                new R("读者005", "13800000005", "PUBLIC", "社会读者"),
                new R("读者006", "13800000006", "STUDENT", "经济学院")
        );
        int seq = 1001;
        for (R r : readers) {
            Reader reader = new Reader();
            reader.setCardNo("R2024" + seq++);
            reader.setName(r.name());
            reader.setPhone(r.phone());
            reader.setEmail("");
            reader.setType(r.type());
            reader.setDepartment(r.dept());
            reader.setStatus("ACTIVE");
            reader.setUnpaidFine(BigDecimal.ZERO);
            reader.setBorrowedNow(0);
            reader.setTotalBorrow(0);
            reader.setRemark("");
            reader.setDeleted(0);
            readerMapper.insert(reader);
        }
    }

    private void seedLoans() {
        Random random = new Random(20241117L);
        List<Book> books = bookMapper.selectList(null);
        List<Reader> readers = readerMapper.selectList(null);
        settingService.all();
        for (int i = 0; i < 14; i++) {
            Book book = books.get(random.nextInt(books.size()));
            Reader reader = readers.get(random.nextInt(readers.size()));
            int daysAgo = 5 + random.nextInt(70);
            LocalDateTime borrowed = LocalDateTime.now().minusDays(daysAgo);
            int loanDays = settingService.loanDays(reader.getType());
            LocalDateTime due = borrowed.plusDays(loanDays);
            boolean returned = random.nextBoolean();
            Loan loan = new Loan();
            loan.setBookId(book.getId());
            loan.setReaderId(reader.getId());
            loan.setBorrowedAt(borrowed);
            loan.setDueAt(due);
            loan.setRenewCount(0);
            loan.setFineAmount(BigDecimal.ZERO);
            loan.setFinePaid(0);
            loan.setRemark("");
            loan.setOperator("馆员A");
            if (returned) {
                LocalDateTime back = borrowed.plusDays(3 + random.nextInt(30));
                if (back.isBefore(borrowed)) {
                    back = borrowed.plusDays(1);
                }
                long late = Math.max(0, java.time.Duration.between(due, back).toDays());
                loan.setReturnedAt(back);
                loan.setStatus("RETURNED");
                if (late > 0) {
                    BigDecimal fine = new BigDecimal("0.50").multiply(BigDecimal.valueOf(late));
                    loan.setFineAmount(fine.min(new BigDecimal("50.00")));
                }
                loanMapper.insert(loan);
            } else {
                loan.setStatus("BORROWED");
                loanMapper.insert(loan);
                readerMapper.adjustBorrowed(reader.getId(), 1, 1);
                bookMapper.decreaseAvailable(book.getId());
            }
        }
    }
}
