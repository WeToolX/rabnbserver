package com.ra.rabnbserver.VO.team;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 团队大小区查询结果
 */
@Data
public class TeamAreaResultVO {
    /**
     * 区域类型：1-大区，2-小区
     */
    private Integer type;

    /**
     * 列表记录
     */
    private List<TeamAreaItemVO> records;

    /**
     * 总记录数（用于分页）
     */
    private Long total;

    /**
     * 团队总人数
     */
    private Integer totalTeamCount;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 团队总已购
     */
    private Integer totalPurchasedCount;

    /**
     * 团队总激活
     */
    private Integer totalActiveCount;

    /**
     * 小区总人数
     */
    private Integer smallAreaTeamCount;

    /**
     * 小区总已购
     */
    private Integer smallAreaPurchasedCount;

    /**
     * 小区总激活
     */
    private Integer smallAreaActiveCount;

    /**
     * 当前用户等级
     */
    private Integer currentUserGrade;

    /**
     * 当前用户电费分成比例
     */
    private BigDecimal currentUserElectricityRatio;
    private BigDecimal teamRewardDistributedAmount;
}
