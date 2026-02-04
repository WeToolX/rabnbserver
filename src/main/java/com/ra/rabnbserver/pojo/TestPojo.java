package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.exception.Abnormal.model.AbnormalBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 异常处理框架示例实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("异常处理框架示例表")
@TableName("test")
public class TestPojo extends AbnormalBaseEntity {

    /**
     * 用户标识
     */
    @TableField("user_value")
    @ColumnComment("用户标识")
    @ColumnType("VARCHAR(100)")
    private String userValue;

    /**
     * 业务状态
     * 示例值：SUCCESS / FAIL
     */
    @TableField("biz_status")
    @ColumnComment("业务状态")
    @ColumnType("VARCHAR(50)")
    private String bizStatus;
}
