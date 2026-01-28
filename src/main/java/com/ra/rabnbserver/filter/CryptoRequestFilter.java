package com.ra.rabnbserver.filter;

import com.ra.rabnbserver.crypto.RequestCryptoService;
import com.ra.rabnbserver.crypto.ResponseCryptoService;
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
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 请求体解密过滤器
 */
@Slf4j(topic = "com.ra.rabnbserver.service.crypto")
@Component
@RequiredArgsConstructor
public class CryptoRequestFilter extends OncePerRequestFilter {

    private static final String CONTENT_CUSTOM_JSON = "application/custom-json";
    private static final String CONTENT_KEY_JSON = "application/key-json";
    private static final String INIT_PATH = "/api/user/init";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String INIT_ATTR_TOKEN = "initToken";
    private static final String INIT_ATTR_TS6 = "initTs6";
    private static final String INIT_ATTR_PLAIN = "initPlainJson";

    private final RequestCryptoService requestCryptoService;
    private final ResponseCryptoService responseCryptoService;
    private final TokenExtractor tokenExtractor;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(ADMIN_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isInitRequest(request)) {
            handleInitRequest(request, response, filterChain);
            return;
        }
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
            writeError(response, HttpStatus.UNAUTHORIZED, "缺少 Account-token");
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

    private boolean isInitRequest(HttpServletRequest request) {
        return INIT_PATH.equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod());
    }

    private void handleInitRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws IOException, ServletException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        String contentType = request.getContentType();

        if (isEncryptedContentType(contentType)) {
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
                writeError(response, HttpStatus.UNAUTHORIZED, "缺少 Account-token");
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
            filterChain.doFilter(wrapped, responseWrapper);
        } else {
            filterChain.doFilter(request, responseWrapper);
        }

        if (responseWrapper.getStatus() >= HttpStatus.BAD_REQUEST.value()) {
            responseWrapper.copyBodyToResponse();
            return;
        }
        String plainJson = readCachedBody(responseWrapper);
        String token = Objects.toString(request.getAttribute(INIT_ATTR_TOKEN), "");
        String ts6 = Objects.toString(request.getAttribute(INIT_ATTR_TS6), "");
        String plainFromAttr = Objects.toString(request.getAttribute(INIT_ATTR_PLAIN), "");
        if (StringUtils.hasText(plainFromAttr)) {
            plainJson = plainFromAttr;
        }
        if (!StringUtils.hasText(plainJson) || !StringUtils.hasText(token) || !StringUtils.hasText(ts6)) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "初始化加密参数缺失");
            return;
        }
        String mdKeys = com.ra.rabnbserver.crypto.CryptoUtils.md5Hex(token + ts6);
        String cipherMorse = responseCryptoService.encryptToMorseWithKey(plainJson, mdKeys);

        responseWrapper.resetBuffer();
        responseWrapper.setStatus(HttpStatus.OK.value());
        responseWrapper.setCharacterEncoding(StandardCharsets.UTF_8.name());
        responseWrapper.setContentType(MediaType.TEXT_PLAIN_VALUE);
        responseWrapper.getWriter().write(cipherMorse);
        responseWrapper.copyBodyToResponse();
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

    private String readCachedBody(ContentCachingResponseWrapper responseWrapper) {
        byte[] bytes = responseWrapper.getContentAsByteArray();
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
