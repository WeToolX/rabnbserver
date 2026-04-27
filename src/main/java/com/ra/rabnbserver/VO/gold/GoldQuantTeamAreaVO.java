package com.ra.rabnbserver.VO.gold;

import lombok.Data;

@Data
public class GoldQuantTeamAreaVO {
    private Long userId;
    private String walletAddress;
    private Integer teamCount;
    private Integer validWindowCount;
    private Boolean bigArea;
}
