package com.library.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.PageVO;
import com.library.common.R;
import com.library.entity.AuditLog;
import com.library.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LogController {

    private final AuditLogMapper auditLogMapper;

    @GetMapping
    public R<PageVO<AuditLog>> page(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "20") long pageSize) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AuditLog::getAction, kw).or().like(AuditLog::getDetail, kw)
                    .or().like(AuditLog::getUserName, kw));
        }
        wrapper.orderByDesc(AuditLog::getId);
        return R.ok(PageVO.of(auditLogMapper.selectPage(new Page<>(page, pageSize), wrapper)));
    }
}
