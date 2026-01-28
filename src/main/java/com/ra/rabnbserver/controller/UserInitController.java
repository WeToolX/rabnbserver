package com.ra.rabnbserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ra.rabnbserver.crypto.CryptoConstants;
import com.ra.rabnbserver.crypto.CryptoUtils;
import com.ra.rabnbserver.dto.LoginDataDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.utils.RandomIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户接口（初始化、登录）
 */
@Slf4j(topic = "com.ra.rabnbserver.controller.user")
@RestController
@RequestMapping("/api/user")
public class UserInitController {

    private static final String INIT_ATTR_TOKEN = "initToken";
    private static final String INIT_ATTR_TS6 = "initTs6";
    private static final String INIT_ATTR_PLAIN = "initPlainJson";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化接口（返回明文，密文由拦截器处理）
     */
    @PostMapping("/init")
    public String init(HttpServletRequest request, @RequestBody String data) throws Exception {
        log.info("用户初始化接口收到请求，请求体：{}", data);

        String userAgent = request.getHeader("User-Agent");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String shortTimestamp = timestamp.substring(0, Math.min(7, timestamp.length()));
        log.info("用户初始化接口UA: {}, 时间戳: {}", userAgent, shortTimestamp);

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

        String key = CryptoUtils.md5Hex(token + CryptoConstants.TOKEN_SALT);
        StpUtil.getTokenSession().set("Key", key);
        Map<String, Object> plainMap = new HashMap<>();
        plainMap.put("token", token);
        plainMap.put("Key", key);

        String ts6 = timestamp.substring(0, Math.min(6, timestamp.length()));
        String plainJson = objectMapper.writeValueAsString(plainMap);

        request.setAttribute(INIT_ATTR_TOKEN, token);
        request.setAttribute(INIT_ATTR_TS6, ts6);
        request.setAttribute(INIT_ATTR_PLAIN, plainJson);

        log.info("初始化明文数据：{}", plainJson);
        return plainJson;
    }

    /**
     * 用户登录接口
     */
    @PostMapping("/login")
    public String login(HttpServletRequest request, @RequestBody LoginDataDTO loginDataDTO) throws Exception {
        log.info("登录请求传入参数：{}", loginDataDTO);
        if (loginDataDTO.getUserWalletAddress() == null || loginDataDTO.getUserWalletAddress().isEmpty()) {
            return ApiResponse.error("钱包地址不能为空");
        }

        return ApiResponse.success("成功");
    }
}
