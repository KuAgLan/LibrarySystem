package com.library.controller;

import com.library.common.R;
import com.library.lock.MysqlLock;
import com.library.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 任务调度（仅管理员） */
@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class JobController {

    private final JobService jobService;
    private final ObjectProvider<MysqlLock> mysqlLockProvider;

    @PostMapping("/settle")
    public R<Map<String, Object>> settle(@RequestParam(defaultValue = "0") long holdMs) {
        return R.ok(jobService.runNightlySettle(holdMs));
    }

    @GetMapping("/locks")
    public R<Map<String, Object>> locks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", jobService.provider());
        MysqlLock mysqlLock = mysqlLockProvider.getIfAvailable();
        data.put("activeLocks", mysqlLock == null ? List.of() : mysqlLock.activeLocks());
        return R.ok(data);
    }
}