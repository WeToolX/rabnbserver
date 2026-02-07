package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class LoginDataDTO {
    /**
     * 用户钱包地址
     */
    private  String userWalletAddress;

    /**
     * 邀请人钱包地址（或者默认地址（默认邀请码））
     */
    private String code;
}
