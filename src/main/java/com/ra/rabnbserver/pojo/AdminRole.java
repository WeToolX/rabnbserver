package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;

@Data
@TableName("admin_role")
/**
 * 管理员角色表
 */
public class AdminRole extends BaseEntity {
    /**
     * 角色名
     */
    @TableField("role_name")
    @ColumnComment("角色名")
    private String roleName;
    /**
     * 标识符，如 super_admin, manager
     */
    @TableField("role_key")
    @ColumnComment("标识符，如 super_admin, manager")
    private String roleKey;
    /*
    父角色ID，用于权限继承
     */
    @ColumnComment("父角色ID，用于权限继承")
    @TableField("parent_id")
    private Long parentId;
}