package com.library.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/** 分页结构 */
public record PageVO<T>(List<T> items, long total, long page, long pageSize, long pages) {

    public static <T> PageVO<T> of(IPage<T> page) {
        long size = Math.max(1, page.getSize());
        return new PageVO<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(),
                Math.max(1, (long) Math.ceil(page.getTotal() / (double) size)));
    }
}
