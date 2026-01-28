package com.ra.rabnbserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ra.rabnbserver.crypto.CryptoConstants;
import com.ra.rabnbserver.crypto.CryptoUtils;
import com.ra.rabnbserver.dto.LoginDataDTO;
import com.ra.rabnbserver.dto.RegisterDataDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.userServe;
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

    private final userServe userService;

    public UserInitController(userServe userService) {
        this.userService = userService;
    }


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
        log.info("登录请求：{}", loginDataDTO);
        String walletAddress = loginDataDTO.getUserWalletAddress();
        if (StrUtil.isBlank(walletAddress)) {
            return ApiResponse.error("钱包地址不能为空");
        }
        User user = userService.getByWalletAddress(walletAddress);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        upgradeToUserSession(user.getId().toString());
        return ApiResponse.success("登录成功", user);
    }

    /**
     * 用户注册接口
     */
    @PostMapping("/register")
    public String register(HttpServletRequest request, @RequestBody RegisterDataDTO registerDataDTO) throws Exception {
        log.info("注册请求：{}", registerDataDTO);
        String walletAddress = registerDataDTO.getUserWalletAddress();
        if (StrUtil.isBlank(walletAddress)) {
            return ApiResponse.error("钱包地址不能为空");
        }
        User existingUser = userService.getByWalletAddress(walletAddress);
        if (existingUser != null) {
            return ApiResponse.error("该地址已注册");
        }
        User newUser = userService.register(walletAddress);
        upgradeToUserSession(newUser.getId().toString());
        return ApiResponse.success("注册成功", newUser);
    }

    /**
     * 核心复用方法：将当前的临时 Token 绑定到正式用户 ID
     * @param realUserId 真实的数据库用户ID
     */
    private void upgradeToUserSession(String realUserId) {
        String currentToken = StpUtil.getTokenValue();
        Object cryptoKey = StpUtil.getTokenSession().get("Key");
        StpUtil.login(realUserId, new SaLoginParameter()
                .setToken(currentToken) // 强制指定 Token
                .setIsLastingCookie(true)
                .setTimeout(60 * 60 * 24)
        );
        StpUtil.getTokenSession().set("Key", cryptoKey);
        log.info("用户ID {} 已成功绑定到原有 Token {}", realUserId, currentToken);
    }
}
