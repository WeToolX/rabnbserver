package com.ra.rabnbserver.dto.user;

import lombok.Data;

// 分页及筛选请求对象
@Data
public class UserQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String userWalletAddress;
    private String startTime; // 注册开始时间
    private String endTime;   // 注册结束时间
}
