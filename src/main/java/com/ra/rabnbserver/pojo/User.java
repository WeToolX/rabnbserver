package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.annotation.Index;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.IndexType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("用户表")
@Index(name = "uk_user_wallet_address", columns = {"user_wallet_address"}, type = IndexType.UNIQUE, comment = "用户钱包地址唯一索引")
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
     * 用户等级，0表示无等级，达到阶梯后从1级开始，系统根据直属下级矿机数量自动计算
     */
    @TableField("user_grade")
    @ColumnComment("用户等级，0表示无等级，达到阶梯后从1级开始，系统根据直属下级矿机数量自动计算")
    @DefaultValue("0")
    private Integer userGrade = 0;

    /**
     * 管理员自定义的用户等级。0 表示未进行手动覆盖。
     */
    @TableField("custom_user_grade")
    @ColumnComment("管理员自定义的用户等级。0 表示未进行手动覆盖")
    @DefaultValue("0")
    private Integer customUserGrade = 0;

    public Integer getUserGrade() {
        int autoGrade = userGrade == null ? 0 : userGrade;
        int customGrade = customUserGrade == null ? 0 : customUserGrade;
        int finalGrade = Math.max(autoGrade, customGrade);
        return finalGrade <= 0 ? 0 : finalGrade;
    }

    @JsonIgnore
    public Integer getAutoUserGrade() {
        return userGrade == null || userGrade <= 0 ? 0 : userGrade;
    }

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

    /**
     * 碎片余额(作为余额缓存)
     */
    @TableField("fragment_balance")
    @ColumnComment("碎片余额(作为缓存)")
    @DefaultValue("0")
    private String fragmentBalance;

}
