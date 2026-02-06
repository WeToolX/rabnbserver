package com.ra.rabnbserver.exception.Abnormal.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异常框架人工处理路由注册表
 */
@Component
public class AbnormalManualRouteRegistry {

    private final Map<String, AbnormalManualRouteInfo> routeMap = new ConcurrentHashMap<>();

    /**
     * 注册人工处理路由
     *
     * @param route 路由
     * @param info 路由信息
     * @return 是否注册成功
     */
    public boolean register(String route, AbnormalManualRouteInfo info) {
        if (route == null || info == null) {
            return false;
        }
        return routeMap.putIfAbsent(route, info) == null;
    }

    /**
     * 获取路由信息
     *
     * @param route 路由
     * @return 路由信息
     */
    public AbnormalManualRouteInfo get(String route) {
        return routeMap.get(route);
    }
}
