package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class AdminUserLoginDTO {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
}
