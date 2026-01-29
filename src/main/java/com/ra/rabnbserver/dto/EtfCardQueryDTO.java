package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class EtfCardQueryDTO {
    private String batchName;
    private String batchNo;
    private Integer status;
    private Integer isCurrent;
    private Integer page = 1;
    private Integer size = 10;
}