package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class RegisterDataDTO {
    /**
     * 用户钱包地址
     */
    private String userWalletAddress;

    /**
     * 用户注册邀请码
     */
    private String code;
}
