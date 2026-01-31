package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("用户表")
@TableName("user")
public class User extends BaseEntity {

    /**
     *用户名
     */
    @TableField("user_name")
    @ColumnComment("用户名")
    private String userName;


    /**
     * 用户钱包地址
     */
    @TableField("user_wallet_address")
    @ColumnComment("用户钱包地址")
    private String userWalletAddress;


    /**
     * 用户账户余额(作为余额缓存，实际余额需要根据账本数据进行计算)
     */
    @ColumnComment("账户余额(作为余额缓存，实际余额需要根据账本数据进行计算)")
    @TableField("balance")
    @DefaultValue("0.000000000000000000")
    @ColumnType("DECIMAL(36, 18)")
    private BigDecimal balance;

    /**
     * 用户自己的邀请码
     */
    @TableField("invite_code")
    @ColumnComment("用户自己的邀请码")
    private String inviteCode;

    /**
     * 注册填入的邀请码（上级邀请码）
     */
    @TableField("parent_invite_code")
    @ColumnComment("注册填入的邀请码（上级邀请码，后台添加的用户设置为0）")
    @DefaultValue("0")
    private String parentInviteCode;

    /**
     * 上级用户ID (建议增加此字段，方便数据库关联查询，提升性能)
     */
    @TableField("parent_id")
    @ColumnComment("上级用户ID")
    @DefaultValue("0")
    private Long parentId;

    /**
     * 当前层级(根节点为1)
     */
    @TableField("level")
    @ColumnComment("当前层级(根节点为1)")
    @DefaultValue("1")
    private Integer level;

    /**
     * 家族路径(0,id1,id2,...)
     */
    @TableField("path")
    @ColumnComment("家族路径(0,id1,id2,...)")
    @DefaultValue("0,")
    private String path;

    /**
     * 直推人数(仅直接下级)
     */
    @TableField("direct_count")
    @ColumnComment("直推人数(仅直接下级)")
    @DefaultValue("0")
    private Integer directCount;

    /**
     * 团队总人数(所有下级总和)
     */
    @TableField("team_count")
    @ColumnComment("团队总人数(所有下级总和)")
    @DefaultValue("0")
    private Integer teamCount;

}
