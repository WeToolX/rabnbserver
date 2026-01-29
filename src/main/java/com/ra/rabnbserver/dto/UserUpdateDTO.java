package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private Long id;
    private String userName;
    private String userWalletAddress;
}