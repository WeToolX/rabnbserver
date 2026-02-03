package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员权限表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_permission")
@TableComment("管理员权限表")
public class AdminPermission extends BaseEntity {

    /**
     * 权限名称(如: 用户删除)
     */
    @TableField("name")
    @ColumnComment("权限名称(如: 用户删除)")
    private String name;

    /**
     * 权限标识符(如: user:delete)
     */
    @TableField("perm_key")
    @ColumnComment("权限标识符(如: user:delete)")
    private String permKey;


    /**
     * 描述
     */
    @TableField("description")
    @ColumnComment("描述")
    private String description;
}