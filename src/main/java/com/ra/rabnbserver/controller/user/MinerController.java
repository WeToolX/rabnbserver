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
import com.ra.rabnbserver.server.miner.impl.MinerPurchaseRetryServeImpl;
import com.ra.rabnbserver.server.miner.impl.MinerProfitRetryServeImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user/miner")
@RequiredArgsConstructor
public class MinerController {

    private final MinerServe minerServe;
    private final MinerPurchaseRetryServeImpl purchaseRetryServe;
    private final MinerProfitRetryServeImpl profitRetryServe;

    /**
     * 分页查询我的矿机列表
     */
    @SaCheckLogin
    @PostMapping("/list")
    public String getMinerList(@RequestBody(required = false) MinerQueryDTO query) {
        if (query == null) query = new MinerQueryDTO();
        Long userId = getFormalUserId();
        try {
            IPage<UserMiner> result = minerServe.getUserMinerPage(userId, query);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 购买矿机
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
     * 异常处理：人工干预-矿机激活/购买成功（卡牌兑换成功）回调
     * 当自动重试多次失败或超时后，管理员通过此接口手动同步状态
     */
    @SaCheckLogin
    @PostMapping("/manual-purchase-success")
    public String manualPurchaseSuccess(@RequestParam("dataId") Long dataId) {
        log.info("人工干预：矿机购买激活成功回调，ID: {}", dataId);
        purchaseRetryServe.ProcessingSuccessful(dataId);
        return ApiResponse.success("人工处理成功", dataId);
    }

    /**
     * 异常处理：人工干预-矿机收益发放成功回调
     * 管理员在后台手动补发合约收益后，通过此接口回写数据库
     */
    @SaCheckLogin
    @PostMapping("/manual-profit-success")
    public String manualProfitSuccess(@RequestParam("dataId") Long dataId) {
        log.info("人工干预：收益发放成功回调，ID: {}", dataId);
        profitRetryServe.ProcessingSuccessful(dataId);
        return ApiResponse.success("人工处理成功", dataId);
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