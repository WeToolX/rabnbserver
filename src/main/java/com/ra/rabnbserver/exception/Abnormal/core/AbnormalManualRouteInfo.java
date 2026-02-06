package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.Getter;

/**
 * 异常框架人工处理路由信息
 */
@Getter
public class AbnormalManualRouteInfo {

    /**
     * 路由地址
     */
    private final String route;

    /**
     * 服务名称
     */
    private final String serviceName;

    /**
     * 表名
     */
    private final String table;

    /**
     * 业务服务实例
     */
    private final AbstractAbnormalRetryService service;

    public AbnormalManualRouteInfo(String route, String serviceName, String table, AbstractAbnormalRetryService service) {
        this.route = route;
        this.serviceName = serviceName;
        this.table = table;
        this.service = service;
    }
}
