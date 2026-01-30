package com.ra.rabnbserver.controller.sys;


import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.dto.contract.TreasuryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.server.user.userBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
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
    @PostMapping("/payment-usdt/min-amount")
    public String setMinAmount(@RequestParam BigInteger amount) throws Exception {
        // 假设合约有 setMinAmount 方法
        //var receipt = paymentUsdtContract.setMinAmount(amount); receipt.getTransactionHash()
        return ApiResponse.success("暂无该设置");
    }
}