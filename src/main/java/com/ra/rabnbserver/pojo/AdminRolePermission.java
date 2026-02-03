package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 角色权限关联表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_role_permission")
@TableComment("角色权限关联表")
public class AdminRolePermission extends BaseEntity {

    /**
     * 角色ID
     */
    @TableField("role_id")
    @ColumnComment("角色ID")
    private Long roleId;

    /**
     * 权限ID
     */
    @TableField("permission_id")
    @ColumnComment("权限ID")
    private Long permissionId;
}