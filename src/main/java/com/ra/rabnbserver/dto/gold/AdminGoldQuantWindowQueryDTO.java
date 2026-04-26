package com.ra.rabnbserver.dto.gold;

import lombok.Data;

@Data
public class AdminGoldQuantWindowQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private Long userId;
    private String walletAddress;
    private String windowNo;
    private Integer status;
    private String expireStartTime;
    private String expireEndTime;
    private String startTime;
    private String endTime;
}
