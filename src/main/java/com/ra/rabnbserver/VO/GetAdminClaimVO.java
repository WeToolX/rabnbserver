package com.ra.rabnbserver.VO;

import lombok.Data;

@Data
public class GetAdminClaimVO {
    /** 仓位类型: 1(L1), 2(L2), 3(L3) */
    private Integer lockType;

}
