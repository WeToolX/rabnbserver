package com.ra.rabnbserver.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权配置
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器，统一处理登录校验
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> SaRouter
                        .match("/**")
                        .notMatch("/api/user/init")
                        .notMatch("/api/user/login")
                        .notMatch("/api/user/register")
                        .notMatch("/api/admin/**")
                        .notMatch("/api/common/**")
                        .notMatch("/swagger-ui/**")
                        .notMatch(SaHttpMethod.OPTIONS)
                        .check(r -> StpUtil.checkLogin())
                ))
                .addPathPatterns("/**");
    }
}
