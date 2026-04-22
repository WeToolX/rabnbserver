package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.VO.GetAdminClaimVO;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerProfitRecordQueryDTO;
import com.ra.rabnbserver.dto.MinerPurchaseDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.dto.adminMinerAction.AdminMinerActionDTO;
import com.ra.rabnbserver.dto.adminMinerAction.FragmentExchangeNftDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerProfitRecordServe;
import com.ra.rabnbserver.server.miner.MinerServe;
import com.ra.rabnbserver.server.sys.SystemConfigServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user/miner")
@RequiredArgsConstructor
public class MinerController {
    private final MinerServe minerServe;
    private final MinerProfitRecordServe minerProfitRecordServe;
    private final SystemConfigServe systemConfigServe;

    @Value("${ADMIN.ISOPEN:true}")
    private Boolean ISOPEN;

    @SaCheckLogin
    @PostMapping("/list")
    public String getMinerList(@RequestBody(required = false) MinerQueryDTO query) {
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        if (query == null) {
            query = new MinerQueryDTO();
        }
        log.info("getMinerList,查询参数：{}", query);
        Long userId = getFormalUserId();
        try {
            IPage<UserMiner> result = minerServe.getUserMinerPage(userId, query);
            log.info("getMinerList result={}", result);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/purchase")
    public String purchase(@RequestBody MinerPurchaseDTO dto) {
        Long userId = getFormalUserId();
        if (!ISOPEN) {
            return ApiResponse.error("购买矿机暂未开放！");
        }
        if (StrUtil.isBlank(dto.getMinerType()) || dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return ApiResponse.error("参数错误");
        }
        if (dto.getCardId() == null) {
            return ApiResponse.error("卡牌ID不能为空");
        }
        minerServe.buyMinerBatch(userId, dto.getMinerType(), dto.getQuantity(), dto.getCardId());
        return ApiResponse.success("购买申请提交成功");
    }

    @SaCheckLogin
    @PostMapping("/claim-tokens")
    public String adminClaim(@RequestBody GetAdminClaimVO dto) {
        Long userId = getFormalUserId();
        if (!ISOPEN) {
            return ApiResponse.error("矿机收益暂未开放！");
        }
        if (dto.getLockType() == null) {
            return ApiResponse.error("仓类参数缺失");
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
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        if (dto.getAmount() == null || dto.getAddress() == null || dto.getLockType() == null) {
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
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        if (dto.getAmount() == null || dto.getAddress() == null || dto.getLockType() == null) {
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
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        Long userId = getFormalUserId();
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return ApiResponse.error("兑换数量不合法");
        }
        if (dto.getCardId() == null) {
            return ApiResponse.error("卡牌ID不能为空");
        }
        if (!Integer.valueOf(3).equals(dto.getCardId())) {
            return ApiResponse.error("碎片兑换卡牌仅支持兑换金卡");
        }
        try {
            minerServe.buyNftWithFragments(userId, dto);
            return ApiResponse.success("卡牌兑换成功，已发放至钱包");
        } catch (Exception e) {
            log.error("碎片换卡失败, userId: {}", userId, e);
            return ApiResponse.error(e.getMessage());
        }
    }



    /**
     * 缴纳电费 (激活/续费)
     */
    @SaCheckLogin
    @PostMapping("/pay-electricity")
    public String payElectricity(@RequestBody MinerElectricityDTO dto) {
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        Long userId = getFormalUserId();
        if (dto.getMode() == null) {
            return ApiResponse.error("请选择模式");
        }
        try {
            minerServe.payElectricity(userId, dto);
            return ApiResponse.success("电费缴纳成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取当前电费单价
     */
    @SaCheckLogin
    @GetMapping("/electricity-price")
    public String getElectricityPrice() {
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        Long userId = getFormalUserId();
        MinerSettings settings = systemConfigServe.getConfigObject("MINER_SYSTEM_SETTINGS", MinerSettings.class);
        if (settings == null || settings.getElectricFee() == null) {
            log.warn("电费单价配置缺失，userId={}", userId);
            return ApiResponse.error("电费单价暂未配置");
        }
        log.info("获取电费单价成功，userId={}, 电费单价={}", userId, settings.getElectricFee());
        return ApiResponse.success("获取电费单价成功", settings.getElectricFee());
    }

    @SaCheckLogin
    @PostMapping("/miner-profit-record/list")
    public String getProfitList(@RequestBody MinerProfitRecordQueryDTO queryDTO) {
        if (!ISOPEN) {
            return ApiResponse.error("暂未开放！");
        }
        Long userId = getFormalUserId();
        queryDTO.setUserId(userId);
        return ApiResponse.success(minerProfitRecordServe.findPage(queryDTO));
    }

    /**
     * 根据 Key 直接查询配置值
     */
    @GetMapping("/config/{key}")
    public String getValueByKey(@PathVariable String key) {
        getFormalUserId();
        String configKey = "";
        if ("1".equals(key)) {
            configKey = "MINER_SYSTEM_SETTINGS";
        } else if ("2".equals(key)) {
            configKey = "WITHDRAW_SETTINGS";
        }
        return ApiResponse.success(systemConfigServe.getValueByKey(configKey));
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
