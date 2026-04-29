package com.ra.rabnbserver.dto.user;

import lombok.Data;

@Data
public class UserQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String userWalletAddress;
    private String startTime;
    private String endTime;
    private Integer userGrade;
    private Integer goldQuantRewardLevel;
    private Integer goldQuantDistributionLevel;
}
