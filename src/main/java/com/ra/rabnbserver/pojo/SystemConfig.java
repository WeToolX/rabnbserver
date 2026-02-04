package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置表
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("system_config")
public class SystemConfig extends BaseEntity {

    /**
     * 配置键名
     */
    @TableField("config_key")
    @ColumnComment("配置键名")
    private String configKey;

    /**
     * 配置内容(JSON)
     */
    @TableField("config_value")
    @ColumnComment("配置内容(JSON)")
    @ColumnType("TEXT")
    private String configValue;

    /**
     * 配置说明
     */
    @TableField("description")
    @ColumnComment("配置说明")
    @ColumnType("TEXT")
    private String description;

    /**
     * 配置名称/描述
     */
    @TableField("config_name")
    @ColumnComment("配置名称")
    private String configName;

    /**
     *  备注
     */
    @TableField("remark")
    @ColumnComment("备注")
    @ColumnType("TEXT")
    private String remark;
}