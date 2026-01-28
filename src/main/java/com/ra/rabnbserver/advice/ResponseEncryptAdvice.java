package com.ra.rabnbserver.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.ra.rabnbserver.crypto.ResponseCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应加密处理（返回统一的“明文+密文”结构）
 */
@Slf4j(topic = "com.ra.rabnbserver.service.response")
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseEncryptAdvice implements ResponseBodyAdvice<Object> {

    private final ResponseCryptoService responseCryptoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        if (shouldIgnore(returnType)) {
            return body;
        }
        String plainJson = body == null ? "" : body.toString();
        if (isWrappedStructure(plainJson)) {
            return body;
        }
        if (!StpUtil.isLogin()) {
            return wrapResponse(plainJson, null);
        }
        String token = StpUtil.getTokenValue();
        if (token == null || token.isBlank()) {
            return wrapResponse(plainJson, null);
        }
        try {
            String cipher = responseCryptoService.encryptToMorse(plainJson, token);
            return wrapResponse(plainJson, cipher);
        } catch (Exception e) {
            log.warn("响应加密失败，返回明文结构: {}", e.getMessage());
            return wrapResponse(plainJson, null);
        }
    }

    private boolean isWrappedStructure(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.has("明文") && node.has("密文");
        } catch (Exception e) {
            return false;
        }
    }

    private String wrapResponse(Object body, String cipher) {
        try {
            JsonNode plainNode = null;
            if (body != null) {
                plainNode = objectMapper.readTree(body.toString());
            }
            var wrapper = new java.util.LinkedHashMap<String, Object>();
            wrapper.put("明文", plainNode == null ? null : plainNode);
            wrapper.put("密文", cipher);
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            log.warn("包装响应失败，返回原始内容: {}", e.getMessage());
            return body == null ? "" : body.toString();
        }
    }

    private boolean shouldIgnore(MethodParameter returnType) {
        if (returnType == null) {
            return false;
        }
        if (returnType.getMethod() != null && returnType.getMethod().isAnnotationPresent(IgnoreResponseWrap.class)) {
            return true;
        }
        Class<?> declaringClass = returnType.getDeclaringClass();
        return declaringClass != null && declaringClass.isAnnotationPresent(IgnoreResponseWrap.class);
    }
}
