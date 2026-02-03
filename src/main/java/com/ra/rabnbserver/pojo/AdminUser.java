package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableComment("管理员用户表")
@TableName("admin_user")
public class AdminUser extends BaseEntity {

    @TableField("username")
    @ColumnComment("登录用户名")
    private String username;

    @TableField("password")
    @ColumnComment("登录密码")
    private String password;

    @TableField("role_id")
    @ColumnComment("所属角色ID")
    private Long roleId;

    @TableField("status")
    @ColumnComment("状态(1:正常, 0:禁用)")
    @DefaultValue("1")
    private Integer status;

    @TableField("nickname")
    @ColumnComment("管理员昵称")
    private String nickname;
}