package com.ra.rabnbserver.dto.withdraw;

import lombok.Data;

@Data
public class UserWithdrawQueryDTO {
    /**
     * 页码 默认1
     */
    private Integer page = 1;
    /**
     * 页数量 默认10
     */
    private Integer size = 10;

    /** 状态: 0-待审核, 1-已通过, 2-已驳回 */
    private Integer status;

    /** 提现订单号(支持模糊查询) */
    private String orderId;

    /** 开始时间 (格式: yyyy-MM-dd) */
    private String startDate;

    /** 结束时间 (格式: yyyy-MM-dd) */
    private String endDate;
}
