package com.ra.rabnbserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ra.rabnbserver.dto.AuthMockRequest;
import com.ra.rabnbserver.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权测试接口
 */
@Slf4j(topic = "com.ra.rabnbserver.controller.user")
@RestController
@RequestMapping("/auth")
public class AuthMockController {

    /**
     * 返回当前 token 的 subject 与传入的 userId
     */
    @PostMapping("/mock")
    public String mock(@RequestBody AuthMockRequest request) {
        StpUtil.checkLogin();
        String subject = StpUtil.getLoginIdAsString();
        log.info("鉴权测试接口调用，登录标识={}, userId={}", subject, request.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("subject", subject);
        data.put("userId", request.getUserId());
        return ApiResponse.success(data);
    }
}
