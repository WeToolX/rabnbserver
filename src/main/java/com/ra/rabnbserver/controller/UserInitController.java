package com.ra.rabnbserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ra.rabnbserver.crypto.CryptoConstants;
import com.ra.rabnbserver.crypto.CryptoUtils;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.ra.rabnbserver.advice.IgnoreResponseWrap;
import com.ra.rabnbserver.crypto.ResponseCryptoService;
import com.ra.rabnbserver.utils.RandomIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户初始化接口
 */
@Slf4j(topic = "com.ra.rabnbserver.controller.user")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInitController {

    private final ResponseCryptoService responseCryptoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化接口（仅返回密文字符串）
     */
    @IgnoreResponseWrap
    @PostMapping("/init")
    public String init(HttpServletRequest request, @RequestBody String data) throws Exception {
        log.info("用户初始化接口收到请求，请求体: {}", data);

        String userAgent = request.getHeader("User-Agent");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String shortTimestamp = timestamp.substring(0, Math.min(7, timestamp.length()));
        log.info("用户初始化接口 UA: {}, 时间片: {}", userAgent, shortTimestamp);

        // 生成随机 subject 并登录
        String subject = RandomIdGenerator.generateRandom16ByteHexString();
        StpUtil.login(subject, new SaLoginParameter()
                .setIsLastingCookie(true)
                .setTimeout(60 * 60 * 24)
                .setIsConcurrent(true)
                .setIsShare(true)
                .setMaxLoginCount(12)
                .setMaxTryTimes(12)
                .setIsWriteHeader(true)
                .setTerminalExtra("Key", CryptoUtils.md5Hex(StpUtil.getTokenValue() + CryptoConstants.TOKEN_SALT))
        );
        String token = StpUtil.getTokenValue();

        // 构建明文（仅 token + Key）
        String key = CryptoUtils.md5Hex(token + CryptoConstants.TOKEN_SALT);
        // 同步写入当前 Token 会话，保持与原逻辑一致
        StpUtil.getTokenSession().set("Key", key);
        Map<String, Object> plainMap = new HashMap<>();
        plainMap.put("token", token);
        plainMap.put("Key", key);

        // 生成密文（参考逻辑：mdkeys = MD5(token + ts6)）
        String ts6 = timestamp.substring(0, Math.min(6, timestamp.length()));
        String mdKeys = CryptoUtils.md5Hex(token + ts6);
        String plainJson = objectMapper.writeValueAsString(plainMap);
        String cipherMorse = responseCryptoService.encryptToMorseWithKey(plainJson, mdKeys);
        log.info("初始化明文数据: {}", plainJson);
        return cipherMorse;
    }
}
