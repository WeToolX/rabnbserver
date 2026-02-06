package com.ra.rabnbserver.exception.Abnormal.core;

import com.ra.rabnbserver.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异常框架人工处理统一回调控制器
 * <p>
 * 路由由框架动态注册，不在此处声明 @RequestMapping。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AbnormalManualController {

    private final AbnormalManualRouteRegistry registry;

    /**
     * 人工处理成功回调入口（动态注册）
     *
     * @param dataId 数据主键
     * @param request 请求
     * @return 响应
     */
    public String manualSuccess(@RequestParam("dataId") Long dataId, HttpServletRequest request) {
        String path = resolveRequestPath(request);
        AbnormalManualRouteInfo routeInfo = registry.get(path);
        if (routeInfo == null) {
            log.warn("人工处理回调未注册，path={}, dataId={}", path, dataId);
            return ApiResponse.error("人工处理接口未注册");
        }
        routeInfo.getService().manualSuccessExample(dataId);
        log.info("人工处理成功已回调，服务={}, 表={}, dataId={}",
                routeInfo.getServiceName(), routeInfo.getTable(), dataId);
        return ApiResponse.success("人工处理成功", dataId);
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
