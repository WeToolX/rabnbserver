package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.dto.*;
import com.ra.rabnbserver.dto.adminMinerAction.AdminMinerActionDTO;
import com.ra.rabnbserver.dto.adminMinerAction.FragmentExchangeNftDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerProfitRecordServe;
import com.ra.rabnbserver.server.miner.MinerServe;
import com.ra.rabnbserver.server.miner.impl.MinerPurchaseRetryServeImpl;
import com.ra.rabnbserver.server.miner.impl.MinerProfitRetryServeImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 用户端 - 用户矿机接口
 * （包含用户缴纳矿机电费，购买矿机，购买加速包，获取矿机收益，获取碎片，使用碎片兑换卡牌）
 */
@Slf4j
@RestController
@RequestMapping("/api/user/miner")
@RequiredArgsConstructor
public class MinerController {

    private final MinerServe minerServe;
    private final MinerPurchaseRetryServeImpl purchaseRetryServe;
    private final MinerProfitRetryServeImpl profitRetryServe;
    private  final MinerProfitRecordServe minerProfitRecordServe;

    /**
     * 分页查询我的矿机列表
     */
    @SaCheckLogin
    @PostMapping("/list")
    public String getMinerList(@RequestBody(required = false) MinerQueryDTO query) {
        log.info("getMinerList,查询参数：{}", query.toString());
        if (query == null) query = new MinerQueryDTO();
        Long userId = getFormalUserId();
        try {
            IPage<UserMiner> result = minerServe.getUserMinerPage(userId, query);
            log.info("getMinerList result={}", result);
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
     * 领取矿机收益
     */
    @SaCheckLogin
    @PostMapping("/claim-tokens")
    public String adminClaim(@RequestBody AdminMinerActionDTO dto) {
        if (dto.getOrderId() == null || dto.getLockType() == null) {
            return ApiResponse.error("参数缺失");
        }
        try {
            String txId = minerServe.adminClaimAll(dto);
            return ApiResponse.success("执行成功", txId);
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 兑换未解锁碎片
     */
    @SaCheckLogin
    @PostMapping("/exchange-locked")
    public String adminExchangeLocked(@RequestBody AdminMinerActionDTO dto) {
        if (dto.getAmount() == null || dto.getOrderId() == null) {
            return ApiResponse.error("参数缺失");
        }
        try {
            String txId = minerServe.adminExchangeLocked(dto);
            return ApiResponse.success("未解锁碎片兑换成功", txId);
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 兑换已解锁碎片
     */
    @SaCheckLogin
    @PostMapping("/exchange-unlocked")
    public String adminExchangeUnlocked(@RequestBody AdminMinerActionDTO dto) {
        if (dto.getAmount() == null || dto.getOrderId() == null) {
            return ApiResponse.error("参数缺失");
        }
        try {
            String txId = minerServe.adminExchangeUnlocked(dto);
            return ApiResponse.success("已解锁碎片兑换成功", txId);
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 用户使用碎片兑换卡牌
     */
    @SaCheckLogin
    @PostMapping("/exchange-nft")
    public String exchangeNft(@RequestBody FragmentExchangeNftDTO dto) {
        Long userId = getFormalUserId();
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return ApiResponse.error("兑换数量不合法");
        }
        try {
            minerServe.buyNftWithFragments(userId, dto);
            return ApiResponse.success("卡牌兑换成功，已分发至钱包");
        } catch (Exception e) {
            log.error("碎片换卡失败, userId: {}, 原因: ", userId, e);
            return ApiResponse.error(e.getMessage());
        }
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
     * 条件筛选矿机收益记录表
     * @return
     */
    @SaCheckLogin
    @PostMapping("/miner-profit-record/list")
    public String getProfitList(@RequestBody MinerProfitRecordQueryDTO queryDTO) {
        Long userId = getFormalUserId();
        queryDTO.setUserId(userId);
        return ApiResponse.success(minerProfitRecordServe.findPage(queryDTO));
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