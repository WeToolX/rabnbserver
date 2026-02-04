package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;

/**
 * 异常重试处理基类（提供默认的 checkUserErr / ProcessingSuccessful 实现）
 */
@RequiredArgsConstructor
public abstract class AbstractAbnormalRetryService implements AbnormalRetryHandler {

    private final AbnormalRetryManager abnormalRetryManager;

    @Override
    public void checkUserErr(String userValue) {
        abnormalRetryManager.checkUserErr(getTargetClass(), userValue);
    }

    @Override
    public void ProcessingSuccessful(Long dataId) {
        abnormalRetryManager.processingSuccessful(getTargetClass(), dataId);
    }

    /**
     * 业务异常落库入口
     *
     * @param dataId 业务数据主键
     */
    protected void markAbnormal(Long dataId) {
        abnormalRetryManager.markAbnormal(getTargetClass(), dataId);
    }

    /**
     * 业务异常落库入口（可写入用户标识）
     *
     * @param dataId 业务数据主键
     * @param userValue 用户标识值
     */
    protected void markAbnormal(Long dataId, String userValue) {
        abnormalRetryManager.markAbnormal(getTargetClass(), dataId, userValue);
    }

    /**
     * 获取实际业务类（兼容代理）
     *
     * @return 业务类 Class
     */
    protected Class<?> getTargetClass() {
        return AopUtils.getTargetClass(this);
    }
}
