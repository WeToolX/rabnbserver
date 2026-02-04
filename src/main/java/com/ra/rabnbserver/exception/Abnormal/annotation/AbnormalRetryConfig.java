package com.ra.rabnbserver.exception.Abnormal.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 异常重试配置注解
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbnormalRetryConfig {

    /**
     * 数据库表名
     */
    String table();

    /**
     * 当前服务中文标签（用于邮件/日志）
     */
    String serviceName();

    /**
     * 主键字段（默认 id）
     */
    String idField() default "id";

    /**
     * 用户标识字段
     */
    String userField();

    /**
     * 业务状态字段
     */
    String statusField();

    /**
     * 业务成功值
     * 支持类型：字符串/整数/长整型/布尔（由框架解析）
     */
    String successValue();

    /**
     * 业务失败值
     * 支持类型：字符串/整数/长整型/布尔（由框架解析）
     */
    String failValue();

    /**
     * 最小重试间隔（秒）
     */
    int minIntervalSeconds();

    /**
     * 异常处理超时时间（秒）
     */
    int timeoutSeconds();

    /**
     * 最大自动重试次数
     */
    int maxRetryCount();

    /**
     * 人工提醒间隔（秒）
     */
    int manualRemindIntervalSeconds();
}
