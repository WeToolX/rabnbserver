package com.ra.rabnbserver;

import com.ra.rabnbserver.contract.PaymentUsdtContract;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SecureRandom;

@SpringBootTest
@Slf4j
class RabnbserverApplicationTests {

    @Autowired
    private PaymentUsdtContract paymentUsdtContract;

    @Test
    void contextLoads() {
    }

    /**
     * 测试查询授权额度
     *
     * @throws Exception 异常
     */
    @Test
    void testAllowanceQuery() throws Exception {
        String owner = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        log.info("开始查询授权额度，地址: {}", owner);
        var allowance = paymentUsdtContract.allowanceToPaymentUsdt(owner);
        log.info("授权额度查询结果: {}", allowance);
    }

    /**
     * 测试查询 PaymentUSDT 合约关键地址与参数
     *
     * @throws Exception 异常
     */
    @Test
    void testPaymentUsdtMeta() throws Exception {
        log.info("PaymentUSDT 地址: {}", paymentUsdtContract.getAddress());
        log.info("USDT 地址: {}", paymentUsdtContract.usdtAddress());
        log.info("管理员地址: {}", paymentUsdtContract.adminAddress());
        log.info("执行者地址: {}", paymentUsdtContract.executorAddress());
        log.info("收款地址: {}", paymentUsdtContract.treasuryAddress());
        log.info("最小扣款金额: {}", paymentUsdtContract.minAmount());
    }

    /**
     * 测试扣款：用户扣 66 USDT（6 位小数）
     *
     * @throws Exception 异常
     */
    @Test
    void testDeposit66Usdt() throws Exception {
        String user = "0xa068802d54d2aca1ad8ce6f2300eee02e3b50113";
        BigInteger amount = BigInteger.valueOf(66L).multiply(BigInteger.valueOf(1_000_000L));
        String orderIdHex = generateOrderIdHex();

        log.info("扣款测试开始，订单: {}", orderIdHex);
        log.info("扣款用户地址: {}", user);
        log.info("扣款金额（最小单位）: {}", amount);

        BigInteger allowance = paymentUsdtContract.allowanceToPaymentUsdt(user);
        BigInteger balance = paymentUsdtContract.balanceOf(user);
        log.info("当前授权额度: {}", allowance);
        log.info("当前余额: {}", balance);

        var receipt = paymentUsdtContract.deposit(orderIdHex, user, amount);
        log.info("扣费结果: {}", receipt);
    }

    /**
     * 测试查询订单号是否已执行
     *
     * @throws Exception 异常
     */
    @Test
    void testOrderExecuted() throws Exception {
        String orderIdHex = "0xfbc3c3d0baa01802c82ac24bb7b4dc0b178cff1790f0080042ce0095d3e9a624";
        Boolean executed = paymentUsdtContract.executed(orderIdHex);
        log.info("订单执行状态，订单号: {}, 结果: {}", orderIdHex, executed);
    }

    /**
     * 生成 32 字节订单 ID（hex）
     *
     * @return 订单 ID hex
     */
    private String generateOrderIdHex() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Numeric.toHexString(bytes);
    }
}
