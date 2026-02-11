package com.ra.rabnbserver.exception.Abnormal.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.AbnormalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 异常重试字段基类（需要使用异常框架的表可直接继承）
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class AbnormalBaseEntity extends BaseEntity {

    /**
     * 异常主状态
     */
    @TableField(value = "err_status", fill = FieldFill.INSERT)
    @ColumnComment("异常主状态")
    @ColumnType("INT")
    @DefaultValue("2000")
    private Integer errStatus = AbnormalStatus.NORMAL.getCode();

    /**
     * 首次异常时间
     */
    @TableField(value = "err_start_time", fill = FieldFill.INSERT)
    @ColumnComment("首次异常时间")
    @ColumnType("DATETIME")
    private LocalDateTime errStartTime;

    /**
     * 已重试次数
     */
    @TableField("err_retry_count")
    @ColumnComment("已重试次数")
    @ColumnType("INT")
    @DefaultValue("0")
    private Integer errRetryCount;

    /**
     * 下次允许自动重试时间
     */
    @TableField("err_next_retry_time")
    @ColumnComment("下次允许自动重试时间")
    @ColumnType("DATETIME")
    private LocalDateTime errNextRetryTime;

    /**
     * 最小重试间隔（秒）
     */
    @TableField("err_min_interval")
    @ColumnComment("最小重试间隔(秒)")
    @ColumnType("INT")
    private Integer errMinInterval;

    /**
     * 最大处理窗口（秒）
     */
    @TableField("err_timeout")
    @ColumnComment("最大处理窗口(秒)")
    @ColumnType("INT")
    private Integer errTimeout;

    /**
     * 人工处理状态
     */
    @TableField("err_submit_manual_status")
    @ColumnComment("人工处理状态")
    @ColumnType("INT")
    private Integer errSubmitManualStatus;

    /**
     * 下次提醒人工时间
     */
    @TableField("err_next_remind_staff_time")
    @ColumnComment("下次提醒人工时间")
    @ColumnType("DATETIME")
    private LocalDateTime errNextRemindStaffTime;

    /**
     * 通知人工处理次数
     */
    @TableField("err_manual_notify_count")
    @ColumnComment("通知人工处理次数")
    @ColumnType("INT")
    @DefaultValue("0")
    private Integer errManualNotifyCount;
}
