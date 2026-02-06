package com.ra.rabnbserver.dto;

import lombok.Data;

import java.util.List;

/**
 * 异常记录查询参数
 */
@Data
public class AbnormalQueryDTO {

    /**
     * 页码（从 1 开始）
     */
    private Integer pageNum;

    /**
     * 每页数量
     */
    private Integer pageSize;

    /**
     * 单个服务名筛选
     */
    private String serviceName;

    /**
     * 多个服务名筛选
     */
    private List<String> serviceNameList;

    /**
     * 单个异常状态筛选
     */
    private Integer errStatus;

    /**
     * 多个异常状态筛选
     */
    private List<Integer> errStatusList;
}
