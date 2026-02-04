package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;

/**
 * 异常重试处理基类
 * <p>
 * 作用：
 * 1. 统一封装框架入口方法（校验异常、人工处理成功回写）
 * 2. 提供异常落库的公共方法，减少业务重复代码
 * 3. 自动适配 AOP 代理对象，确保取到真实业务类
 * </p>
 * <p>
 * 使用说明：
 * 业务服务需继承本类，并实现 {@link AbnormalRetryHandler} 中的
 * {@code checkStatus} 与 {@code ExceptionHandling} 方法即可。
 * </p>
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
     * <p>
     * 适用于仅有主键的场景，由框架写入异常字段。
     * </p>
     *
     * @param dataId 业务数据主键
     */
    protected void markAbnormal(Long dataId) {
        abnormalRetryManager.markAbnormal(getTargetClass(), dataId);
    }

    /**
     * 业务异常落库入口（可写入用户标识）
     * <p>
     * 适用于业务需要按用户维度进行异常校验的场景。
     * </p>
     *
     * @param dataId 业务数据主键
     * @param userValue 用户标识值
     */
    protected void markAbnormal(Long dataId, String userValue) {
        abnormalRetryManager.markAbnormal(getTargetClass(), dataId, userValue);
    }

    /**
     * 获取实际业务类（兼容代理）
     * <p>
     * 异常框架以“业务类 + 注解配置”为定位依据，
     * 使用该方法可确保从代理对象中取到真实类型。
     * </p>
     *
     * @return 业务类 Class
     */
    protected Class<?> getTargetClass() {
        return AopUtils.getTargetClass(this);
    }
}
