package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.Reader;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface ReaderMapper extends BaseMapper<Reader> {

    @Update("UPDATE reader SET borrowed_now = borrowed_now + #{delta}, total_borrow = total_borrow + #{totalDelta} WHERE id = #{id}")
    int adjustBorrowed(@Param("id") Long id, @Param("delta") int delta, @Param("totalDelta") int totalDelta);

    @Update("UPDATE reader SET unpaid_fine = unpaid_fine + #{amount} WHERE id = #{id}")
    int addFine(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE reader SET unpaid_fine = 0 WHERE id = #{id}")
    int clearFine(@Param("id") Long id);
}
