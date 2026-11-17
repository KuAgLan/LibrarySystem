package com.library.job;

import com.library.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日结算定时任务（多实例部署时由分布式锁保证只执行一次） */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlySettleJob {

    private final JobService jobService;

    @Scheduled(cron = "${app.job.nightly-cron:0 0 2 * * ?}")
    public void run() {
        try {
            jobService.runNightlySettle();
        } catch (Exception e) {
            log.info("本次结算未执行：{}", e.getMessage());
        }
    }
}