package com.ra.rabnbserver.dto;

import lombok.Data;

// DTO: 分发NFT请求
@Data
public class DistributeNftDTO {
    private Long userId; // 用户ID
    private Integer amount; // 分发数量
}
