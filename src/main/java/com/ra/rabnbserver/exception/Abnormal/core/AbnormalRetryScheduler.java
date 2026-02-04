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

    /**
     * 轮询任务
     */
    @Scheduled(fixedDelayString = "#{@abnormalRetryProperties.scanIntervalSeconds * 1000}")
    public void scan() {
        manager.initializeIfNeeded();
        manager.getAllContexts().forEach(this::processContext);
    }

    private void processContext(AbnormalContext context) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        AbnormalRetryHandler handler = context.getHandler();
        List<AbnormalRecord> records = manager.getAllAbnormalData(context);
        for (AbnormalRecord record : records) {
            transactionTemplate.executeWithoutResult(status -> processWithLock(context, handler, record.getId()));
        }
        List<AbnormalRecord> manualList = manager.getAllAbnormalDataNoticeManually(context);
        for (AbnormalRecord record : manualList) {
            transactionTemplate.executeWithoutResult(status -> notifyManualWithLock(context, record.getId()));
        }
    }

    private void processWithLock(AbnormalContext context, AbnormalRetryHandler handler, Long dataId) {
        try {
            AbnormalRecord locked = manager.lockRecordForUpdate(context, dataId);
            if (!manager.isRetryEligible(context, locked)) {
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
