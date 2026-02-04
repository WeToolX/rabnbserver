package com.ra.rabnbserver.server.test;

import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryHandler;

/**
 * 异常处理框架使用示例服务
 */
public interface TestServe extends AbnormalRetryHandler {

    /**
     * 业务入口示例（必须先检查异常）
     *
     * @param userValue 用户标识
     */
    void entryExample(String userValue);

    /**
     * 业务异常落库示例（调用框架标记异常）
     *
     * @param dataId 数据主键
     * @param userValue 用户标识
     */
    void markAbnormalExample(Long dataId, String userValue);

    /**
     * 写入一条失败的测试支付数据
     *
     * @param userValue 用户标识
     * @return 数据主键
     */
    Long createFailPayment(String userValue);

    /**
     * 人工处理成功示例（回写状态）
     *
     * @param dataId 数据主键
     */
    void manualSuccessExample(Long dataId);
}
