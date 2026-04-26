package com.ra.rabnbserver.controller.card.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.ra.rabnbserver.dto.gold.GoldQuantQuantityDTO;
import com.ra.rabnbserver.dto.gold.GoldQuantWindowRenewDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.server.gold.GoldQuantServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/user/card/gold-quant")
@RequiredArgsConstructor
public class GoldQuantController {
    private final GoldQuantServe goldQuantServe;

    @Value("${ADMIN.ISOPEN:true}")
    private Boolean isOpen;

    @SaCheckLogin
    @GetMapping("/home")
    public String home() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            return ApiResponse.success("获取成功", goldQuantServe.getHome(getFormalUserId()));
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/pay-hosting")
    public String payHosting() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.payHosting(getFormalUserId());
            return ApiResponse.success("托管费缴纳成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("黄金量化托管费缴纳失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/buy-window")
    public String buyWindow(@RequestBody GoldQuantQuantityDTO dto) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.buyWindow(getFormalUserId(), dto == null ? null : dto.getQuantity());
            return ApiResponse.success("新购量化窗口成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("新购黄金量化窗口失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/renew-window")
    public String renewWindow(@RequestBody GoldQuantWindowRenewDTO dto) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.renewWindow(getFormalUserId(), dto == null ? null : dto.getWindowId());
            return ApiResponse.success("窗口续费成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("黄金量化窗口续费失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/batch-renew")
    public String batchRenew(@RequestBody GoldQuantQuantityDTO dto) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.batchRenew(getFormalUserId(), dto == null ? null : dto.getQuantity());
            return ApiResponse.success("维护批量续费成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("黄金量化批量续费失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    private Long getFormalUserId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (!StrUtil.isNumeric(loginId)) {
            throw new BusinessException("请先登录账号");
        }
        return Long.parseLong(loginId);
    }
}
