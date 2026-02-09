package com.ra.rabnbserver.dto.adminMinerAction;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AdminMinerActionDTO {
    /** 用户钱包地址 */
    private String address;
    /** 仓位类型: 1(L1), 2(L2), 3(L3) */
    private Integer lockType;
    /** 业务订单号 (用户维度唯一) */
    private Long orderId;
    /** 目标兑换数量 (仅碎片兑换需要) */
    private BigDecimal amount;
}
