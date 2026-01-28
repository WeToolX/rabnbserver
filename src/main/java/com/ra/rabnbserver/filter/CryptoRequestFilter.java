package com.ra.rabnbserver.filter;

import com.ra.rabnbserver.crypto.RequestCryptoService;
import com.ra.rabnbserver.security.TokenExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 请求体解密过滤器
 */
@Slf4j(topic = "com.ra.rabnbserver.service.crypto")
@Component
@RequiredArgsConstructor
public class CryptoRequestFilter extends OncePerRequestFilter {

    private static final String CONTENT_CUSTOM_JSON = "application/custom-json";
    private static final String CONTENT_KEY_JSON = "application/key-json";

    private final RequestCryptoService requestCryptoService;
    private final TokenExtractor tokenExtractor;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (!isEncryptedContentType(contentType)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean tokenMode = contentType.toLowerCase().contains(CONTENT_CUSTOM_JSON);
        String cipherBody = readBody(request);
        if (!StringUtils.hasText(cipherBody)) {
            writeError(response, HttpStatus.BAD_REQUEST, "请求体为空，无法解密");
            return;
        }

        String sign = request.getHeader("Account-sign");
        if (!StringUtils.hasText(sign)) {
            writeError(response, HttpStatus.BAD_REQUEST, "缺少签名头 Account-sign");
            return;
        }

        String token = tokenExtractor.extract(request);
        if (tokenMode && !StringUtils.hasText(token)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "缺少 Account-token 或 Authorization");
            return;
        }

        String userAgent = request.getHeader("User-Agent");
        RequestCryptoService.DecryptResult result = requestCryptoService.decryptRequest(
                cipherBody,
                sign,
                userAgent,
                token,
                tokenMode
        );

        if (!result.isSuccess()) {
            writeError(response, HttpStatus.UNAUTHORIZED, result.getMessage());
            return;
        }

        String overrideContentType = tokenMode ? MediaType.APPLICATION_JSON_VALUE : null;
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(
                request,
                result.getPlainText().getBytes(StandardCharsets.UTF_8),
                overrideContentType
        );
        filterChain.doFilter(wrapped, response);
    }

    private boolean isEncryptedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.contains(CONTENT_CUSTOM_JSON) || lower.contains(CONTENT_KEY_JSON);
    }

    private String readBody(HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
