package com.ra.rabnbserver.exception.Abnormal.core;

/**
 * 异常重试处理器（业务方必须实现）
 */
public interface AbnormalRetryHandler {

    /**
     * 检查业务状态是否已成功
     *
     * @param dataId 业务数据主键
     * @return true 已成功，false 仍异常
     */
    boolean checkStatus(Long dataId);

    /**
     * 异常重试处理方法
     *
     * @param dataId 业务数据主键
     * @return true 处理成功，false 处理失败（不得抛异常）
     */
    boolean ExceptionHandling(Long dataId);

    /**
     * 校验当前用户是否存在未处理异常
     *
     * @param userValue 用户标识值
     */
    void checkUserErr(String userValue);

    /**
     * 人工处理成功回调
     *
     * @param dataId 业务数据主键
     */
    void ProcessingSuccessful(Long dataId);
}
