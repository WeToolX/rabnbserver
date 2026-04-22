package com.ra.rabnbserver.VO.team;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 团队区域查询结果视图对象
 */
@Data
public class TeamAreaResultVO {
    /**
     * 区域类型：1-大区, 2-小区
     */
    private Integer type;

    /**
     * 列表记录详情
     */
    private List<TeamAreaItemVO> records;

    /**
     * 总记录数（用于分页）
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 总购买人数
     */
    private Integer totalPurchasedCount;

    /**
     * 总活跃人数
     */
    private Integer totalActiveCount;

    /**
     * 当前用户等级
     */
    private Integer currentUserGrade;

    /**
     * 当前用户电力比例/绩效比例
     */
    private BigDecimal currentUserElectricityRatio;
}