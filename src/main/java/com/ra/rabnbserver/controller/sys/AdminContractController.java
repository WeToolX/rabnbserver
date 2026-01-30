package com.ra.rabnbserver.controller.sys;


import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import com.ra.rabnbserver.dto.contract.TreasuryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.server.user.userBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 管理员 - 查询链上合约地址
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/contract")
public class AdminContractController {

    private final userBillServe userBillService;
    private final PaymentUsdtContract paymentUsdtContract;

    /**
     * 查询 PaymentUSDT 合约基础信息
     */
    @GetMapping("/payment-usdt/meta")
    public String getMeta() throws Exception {
        return ApiResponse.success(userBillService.getPaymentUsdtMeta());
    }

    /**
     * 设置收款地址 (Treasury)
     */
    @PostMapping("/payment-usdt/treasury")
    public String setTreasury(@RequestBody TreasuryDTO treasuryDTO) throws Exception {
        if (treasuryDTO.getAddress() == null ||  treasuryDTO.getAddress().equals("")) {
            return ApiResponse.error("地址不能为空");
        }
        log.info("管理员设置新收款地址: {}", treasuryDTO.getAddress());
        var receipt = paymentUsdtContract.setTreasury(treasuryDTO.getAddress());
        return ApiResponse.success("设置成功", receipt.getTransactionHash());
    }

    /**
     * 设置最小扣款金额 (待补充)
     */
    /**
     * 设置最小扣款金额
     */
    @PostMapping("/payment-usdt/min-amount")
    public String setMinAmount(@RequestParam BigDecimal amount) throws Exception {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.error("最小扣款金额必须大于 0");
        }
        log.info("管理员设置最小扣款金额 (人类可读): {}", amount);
        BigInteger rawAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amount);
        var receipt = paymentUsdtContract.setMinAmount(rawAmount);
        return ApiResponse.success("设置成功", receipt.getTransactionHash());
    }
}
