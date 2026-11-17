package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.Book;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BookMapper extends BaseMapper<Book> {

    @Update("UPDATE book SET available_copies = available_copies - 1 WHERE id = #{id} AND available_copies > 0 AND deleted = 0")
    int decreaseAvailable(@Param("id") Long id);

    @Update("UPDATE book SET available_copies = available_copies + 1 WHERE id = #{id} AND available_copies < total_copies")
    int increaseAvailable(@Param("id") Long id);
}
