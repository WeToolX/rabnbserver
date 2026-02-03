package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.dto.MinerAccelerationDTO;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerPurchaseDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/miner")
@RequiredArgsConstructor
public class MinerController {

    private final MinerServe minerServe;
    /**
     * 分页查询我的矿机列表
     * 支持多条件筛选（状态、类型、是否加速、电费情况等）
     */
    @SaCheckLogin
    @PostMapping("/list")
    public String getMinerList(@RequestBody(required = false) MinerQueryDTO query) {
        // 如果前端不传 Body，初始化一个默认对象
        if (query == null) {
            query = new MinerQueryDTO();
        }

        Long userId = getFormalUserId();
        log.info("用户 {} 查询矿机列表, 条件: {}", userId, query);

        try {
            IPage<UserMiner> result = minerServe.getUserMinerPage(userId, query);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            log.error("查询矿机列表失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 购买矿机 (卡牌兑换)
     */
    @SaCheckLogin
    @PostMapping("/purchase")
    public String purchase(@RequestBody MinerPurchaseDTO dto) {
        Long userId = getFormalUserId();
        if (StrUtil.isBlank(dto.getMinerType()) || dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return ApiResponse.error("参数错误");
        }
        minerServe.buyMinerBatch(userId, dto.getMinerType(), dto.getQuantity());
        return ApiResponse.success("购买申请提交成功");
    }

    /**
     * 缴纳电费 (激活/续费)
     */
    @SaCheckLogin
    @PostMapping("/pay-electricity")
    public String payElectricity(@RequestBody MinerElectricityDTO dto) {
        Long userId = getFormalUserId();
        if (dto.getMode() == null) return ApiResponse.error("请选择模式");
        try {
            minerServe.payElectricity(userId, dto);
            return ApiResponse.success("电费缴纳成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 购买加速包
     */
    @SaCheckLogin
    @PostMapping("/buy-acceleration")
    public String buyAcceleration(@RequestBody MinerAccelerationDTO dto) {
        Long userId = getFormalUserId();
        if (dto.getMode() == null) return ApiResponse.error("请选择模式");
        try {
            minerServe.buyAccelerationPack(userId, dto);
            return ApiResponse.success("加速成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取当前登录的正式用户ID
     */
    private Long getFormalUserId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (!StrUtil.isNumeric(loginId)) {
            log.warn("非正式用户访问受限接口: {}", loginId);
            throw new BusinessException("请先登录账号");
        }
        return Long.parseLong(loginId);
    }
}