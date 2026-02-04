package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异常重试调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbnormalRetryScheduler {

    private final AbnormalRetryManager manager;
    private final PlatformTransactionManager transactionManager;
    private final AbnormalRetryProperties properties;

    /**
     * 轮询任务
     */
    @Scheduled(fixedDelayString = "#{@abnormalRetryProperties.scanIntervalSeconds * 1000}")
    public void scan() {
        long start = System.currentTimeMillis();
        log.debug("异常重试扫描开始，当前间隔={}秒", properties.getScanIntervalSeconds());
        manager.initializeIfNeeded();
        manager.getAllContexts().forEach(this::processContext);
        long cost = System.currentTimeMillis() - start;
        log.debug("异常重试扫描结束，耗时={}ms", cost);
    }

    private void processContext(AbnormalContext context) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        AbnormalRetryHandler handler = context.getHandler();
        manager.healAbnormalData(context);
        List<AbnormalRecord> records = manager.getAllAbnormalData(context);
        log.debug("异常重试待处理数量，服务={}, 数量={}", context.getConfig().serviceName(), records.size());
        for (AbnormalRecord record : records) {
            transactionTemplate.executeWithoutResult(status -> processWithLock(context, handler, record.getId()));
        }
        // 超时但未达到最大重试次数的数据，直接升级为人工处理
        int promoted = manager.promoteTimeoutToManual(context);
        log.debug("异常超时升级人工统计，服务={}, 数量={}", context.getConfig().serviceName(), promoted);
        List<AbnormalRecord> manualList = manager.getAllAbnormalDataNoticeManually(context);
        log.debug("异常人工通知待处理数量，服务={}, 数量={}", context.getConfig().serviceName(), manualList.size());
        for (AbnormalRecord record : manualList) {
            transactionTemplate.executeWithoutResult(status -> notifyManualWithLock(context, record.getId()));
        }
    }

    private void processWithLock(AbnormalContext context, AbnormalRetryHandler handler, Long dataId) {
        try {
            AbnormalRecord locked = manager.lockRecordForUpdate(context, dataId);
            if (!manager.isRetryEligible(context, locked)) {
                log.debug("异常重试条件不满足，已跳过，服务={}, 数据ID={}",
                        context.getConfig().serviceName(), dataId);
                return;
            }
            if (manager.shouldUpgradeToManual(context, locked)) {
                log.debug("异常已满足升级人工条件，服务={}, 数据ID={}",
                        context.getConfig().serviceName(), dataId);
                manager.upgradeToManual(context, locked);
                return;
            }
            boolean done = handler.checkStatus(locked.getId());
            if (done) {
                manager.retrySuccessful(context, locked.getId());
                return;
            }
            try {
                handler.ExceptionHandling(locked.getId());
            } catch (Exception e) {
                log.error("异常重试执行异常，服务={}, 数据ID={}, 原因={}",
                        context.getConfig().serviceName(), locked.getId(), e.getMessage());
            }
            boolean doneAfter = handler.checkStatus(locked.getId());
            if (doneAfter) {
                manager.retrySuccessful(context, locked.getId());
            } else {
                manager.retryFailed(context, locked);
            }
        } catch (Exception e) {
            log.error("异常重试处理失败，服务={}, 数据ID={}, 原因={}",
                    context.getConfig().serviceName(), dataId, e.getMessage());
        }
    }

    private void notifyManualWithLock(AbnormalContext context, Long dataId) {
        try {
            AbnormalRecord locked = manager.lockRecordForUpdate(context, dataId);
            if (!manager.isManualNotifyEligible(context, locked)) {
                return;
            }
            manager.notifyManual(context, locked);
        } catch (Exception e) {
            log.error("人工通知失败，服务={}, 数据ID={}, 原因={}",
                    context.getConfig().serviceName(), dataId, e.getMessage());
        }
    }
}
