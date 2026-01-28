package com.ra.rabnbserver.advice;

import cn.dev33.satoken.stp.StpUtil;
import com.ra.rabnbserver.crypto.ResponseCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应加密处理（仅返回密文字符串）
 */
@Slf4j(topic = "com.ra.rabnbserver.service.response")
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseEncryptAdvice implements ResponseBodyAdvice<Object> {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String INIT_PATH = "/api/user/init";

    private final ResponseCryptoService responseCryptoService;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return String.class.equals(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        String path = request.getURI().getPath();
        if (path != null && path.startsWith(ADMIN_PATH_PREFIX)) {
            return body;
        }
        if (INIT_PATH.equals(path)) {
            return body;
        }
        String plainJson = body == null ? "" : body.toString();
        if (!StpUtil.isLogin()) {
            return plainJson;
        }
        String token = StpUtil.getTokenValue();
        if (token == null || token.isBlank()) {
            return plainJson;
        }
        try {
            return responseCryptoService.encryptToMorse(plainJson, token);
        } catch (Exception e) {
            log.warn("响应加密失败，返回明文：{}", e.getMessage());
            return plainJson;
        }
    }
}
