package library;

import library.Models.*;

import java.sql.Connection;
import java.sql.SQLException;

/** 示例数据：10 本书 + 4 位读者。 */
public final class SampleData {

    private SampleData() { }

    public static void seed() throws SQLException {
        LibraryService svc = new LibraryService();
        if (!svc.searchBooks(null).isEmpty()) return;

        Object[][] books = {
            { "9787111544937", "深入理解计算机系统", "Randal E. Bryant", "计算机", 3 },
            { "9787111407010", "算法导论", "Thomas H. Cormen", "计算机", 2 },
            { "9787115478033", "数据库系统概念", "Abraham Silberschatz", "计算机", 2 },
            { "9787115546081", "图解 HTTP", "上野宣", "计算机", 1 },
            { "9787020002207", "红楼梦", "曹雪芹", "文学", 4 },
            { "9787532777015", "活着", "余华", "文学", 3 },
            { "9787506365437", "百年孤独", "加西亚·马尔克斯", "文学", 2 },
            { "9787508647357", "人类简史", "尤瓦尔·赫拉利", "历史", 2 },
            { "9787100158602", "国富论", "亚当·斯密", "经济", 1 },
            { "9787521705967", "经济学原理", "曼昆", "经济", 2 },
        };
        for (Object[] b : books) {
            Book book = new Book();
            book.isbn = (String) b[0];
            book.title = (String) b[1];
            book.author = (String) b[2];
            book.category = (String) b[3];
            book.total = (Integer) b[4];
            book.available = book.total;
            svc.addBook(book);
        }

        String[][] readers = {
            { "20240001", "张三", "13800000001" },
            { "20240002", "李四", "13800000002" },
            { "20240003", "王五", "13800000003" },
            { "20240004", "赵六", "13800000004" },
        };
        for (String[] r : readers) {
            Reader reader = new Reader();
            reader.cardNo = r[0];
            reader.name = r[1];
            reader.phone = r[2];
            svc.addReader(reader);
        }
    }
}
