package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import jakarta.annotation.Resource;

import java.lang.reflect.Method;

/**
 * 异常框架人工处理路由自动注册器
 */
@Slf4j
@Component
public class AbnormalManualEndpointRegistrar implements ApplicationRunner {

    @Autowired
    private AbnormalRetryManager abnormalRetryManager;
    @Autowired
    private AbnormalManualRouteRegistry registry;
    @Autowired
    private AbnormalManualController manualController;
    @Resource(name = "requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Override
    public void run(ApplicationArguments args) {
        abnormalRetryManager.initializeIfNeeded();
        Method handlerMethod = resolveHandlerMethod();
        if (handlerMethod == null) {
            return;
        }
        for (AbnormalContext context : abnormalRetryManager.getAllContexts()) {
            AbnormalRetryHandler handler = context.getHandler();
            if (!(handler instanceof AbstractAbnormalRetryService service)) {
                continue;
            }
            String route = normalizeRoute(service.manualSuccessRoute());
            if (!StringUtils.hasText(route)) {
                log.warn("人工处理回调路由为空，已跳过注册，服务={}", context.getConfig().serviceName());
                continue;
            }
            AbnormalManualRouteInfo info = new AbnormalManualRouteInfo(
                    route,
                    context.getConfig().serviceName(),
                    context.getConfig().table(),
                    service
            );
            if (!registry.register(route, info)) {
                log.warn("人工处理路由已存在，忽略注册，route={}, 服务={}", route, info.getServiceName());
                continue;
            }
            registerMapping(route, handlerMethod);
        }
    }

    private Method resolveHandlerMethod() {
        try {
            return AbnormalManualController.class.getMethod("manualSuccess", Long.class, jakarta.servlet.http.HttpServletRequest.class);
        } catch (NoSuchMethodException e) {
            log.error("人工处理回调方法未找到，异常框架无法注册路由");
            return null;
        }
    }

    private void registerMapping(String route, Method handlerMethod) {
        try {
            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(route)
                    .methods(RequestMethod.POST)
                    .build();
            HandlerMethod existing = mapping.getHandlerMethods().get(mappingInfo);
            if (existing != null) {
                log.warn("人工处理路由已存在，跳过注册，route={}", route);
                return;
            }
            mapping.registerMapping(mappingInfo, manualController, handlerMethod);
            log.info("人工处理路由注册成功，route={}", route);
        } catch (Exception e) {
            log.error("人工处理路由注册失败，route={}, 原因={}", route, e.getMessage());
        }
    }

    private String normalizeRoute(String route) {
        if (!StringUtils.hasText(route)) {
            return null;
        }
        String normalized = route.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
