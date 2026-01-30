package com.ra.rabnbserver;

import com.ra.rabnbserver.contract.AionContract;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.service.AionService;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

@SpringBootTest
@Slf4j
class RabnbserverApplicationTests {

    @Autowired
    private PaymentUsdtContract paymentUsdtContract;

    @Autowired
    private AionContract aionContract;

    @Autowired
    private CardNftContract cardNftContract;

    @Autowired
    private AionService aionService;

    /**
     * 方法作用：基础上下文启动校验
     */
    @Test
    void contextLoads() {
    }

    // ===================== 通用工具 =====================
    /**
     * 方法作用：校验 TODO 字符串是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private String requireTodoString(String label, String value) {
        if (value == null || value.startsWith("TODO")) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：校验 TODO 金额是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private BigDecimal requireTodoAmount(String label, BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：校验 TODO 原始数量是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private BigInteger requireTodoRaw(String label, BigInteger value) {
        if (value == null) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：校验 TODO 列表是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private List<String> requireTodoList(String label, List<String> value) {
        if (value == null || value.isEmpty() || "TODO".equals(value.get(0))) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：生成订单号（bytes32 hex）
     *
     * @return 订单号
     */
    private String generateOrderIdHex() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Numeric.toHexString(bytes);
    }

    // ===================== AionContract（只读）=====================

    /**
     * 方法作用：查询 AION 是否暂停
     */
    @Test
    void testAionPaused() throws Exception {
        log.info("AION paused: {}", aionContract.paused());
    }

    /**
     * 方法作用：查询固定价格开关
     */
    @Test
    void testAionFixedPriceEnabled() throws Exception {
        log.info("AION fixedPriceEnabled: {}", aionContract.fixedPriceEnabled());
    }

    /**
     * 方法作用：查询固定价格金额
     */
    @Test
    void testAionFixedPriceAmount() throws Exception {
        log.info("AION fixedPriceAmount: {}", aionContract.fixedPriceAmount());
    }

    /**
     * 方法作用：查询销毁比例
     */
    @Test
    void testAionBurnBps() throws Exception {
        log.info("AION burnBps: {}", aionContract.burnBps());
    }

    /**
     * 方法作用：查询社区比例
     */
    @Test
    void testAionCommunityBps() throws Exception {
        log.info("AION communityBps: {}", aionContract.communityBps());
    }

    /**
     * 方法作用：查询总供应量
     */
    @Test
    void testAionTotalSupply() throws Exception {
        log.info("AION totalSupply: {}", aionContract.totalSupply());
    }

    /**
     * 方法作用：查询指定地址余额
     */
    @Test
    void testAionBalanceOf() throws Exception {
        String account = requireTodoString("AION balanceOf 地址", "TODO:填写地址");
        log.info("AION balanceOf({}): {}", account, aionContract.balanceOf(account));
    }

    /**
     * 方法作用：查询合约自身余额
     */
    @Test
    void testAionBalanceOfSelf() throws Exception {
        log.info("AION balanceOf(address(this)): {}", aionContract.balanceOfSelf());
    }

    /**
     * 方法作用：查询合约 owner
     */
    @Test
    void testAionOwner() throws Exception {
        log.info("AION owner: {}", aionContract.owner());
    }

    /**
     * 方法作用：查询最大供应量 CAP
     */
    @Test
    void testAionCap() throws Exception {
        log.info("AION CAP: {}", aionContract.cap());
    }

    /**
     * 方法作用：查询 ADMIN_ROLE
     */
    @Test
    void testAionAdminRole() throws Exception {
        log.info("AION ADMIN_ROLE: {}", aionContract.adminRole());
    }

    /**
     * 方法作用：查询地址是否拥有指定角色
     */
    @Test
    void testAionHasRole() throws Exception {
        String role = aionContract.adminRole();
        String account = requireTodoString("AION hasRole 地址", "TODO:填写地址");
        log.info("AION hasRole({}, {}): {}", role, account, aionContract.hasRole(role, account));
    }

    /**
     * 方法作用：查询用户锁仓列表
     */
    @Test
    void testAionLocksOf() throws Exception {
        String user = requireTodoString("AION locksOf 地址", "TODO:填写地址");
        var records = aionContract.locksOf(user);
        if (records == null) {
            log.info("锁仓列表为空或未返回，地址: {}", user);
            return;
        }
        log.info("锁仓记录数量: {}", records.size());
        for (int i = 0; i < records.size(); i++) {
            AionContract.LockRecord record = records.get(i);
            BigInteger rawAmount = record.getAmount().getValue();
            var humanAmount = AmountConvertUtils.toHumanAmount(
                    AmountConvertUtils.Currency.AION,
                    rawAmount,
                    6
            );
            log.info("第{}条锁仓: amount={}, unlockTime={}, claimed={}",
                    i + 1,
                    rawAmount,
                    record.getUnlockTime().getValue(),
                    record.getClaimed().getValue());
            log.info("第{}条锁仓(可读): amount={}", i + 1, humanAmount);
        }
    }

    // ===================== AionContract（写操作）=====================

    /**
     * 方法作用：管理员分发 AION（锁仓）
     */
    @Test
    void testAionFaucetMint() throws Exception {
        String to = requireTodoString("AION faucetMint 地址", "TODO:填写地址");
        BigDecimal amountHuman = requireTodoAmount("AION 发放数量", null);
        int plan = 1; // TODO: 0/1/2
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, amountHuman);
        log.info("发放 AION，地址: {}, 数量(原始): {}, plan: {}", to, amount, plan);
        var receipt = aionContract.faucetMint(to, amount, plan);
        log.info("发放结果: {}", receipt);
    }

    /**
     * 方法作用：设置管理员地址
     */
    @Test
    void testAionSetAdmin() throws Exception {
        String newAdmin = requireTodoString("AION setAdmin 地址", "TODO:填写地址");
        var receipt = aionContract.setAdmin(newAdmin);
        log.info("setAdmin 结果: {}", receipt);
    }

    /**
     * 方法作用：撤销管理员地址
     */
    @Test
    void testAionRevokeAdmin() throws Exception {
        String admin = requireTodoString("AION revokeAdmin 地址", "TODO:填写地址");
        var receipt = aionContract.revokeAdmin(admin);
        log.info("revokeAdmin 结果: {}", receipt);
    }

    /**
     * 方法作用：暂停合约
     */
    @Test
    void testAionPause() throws Exception {
        var receipt = aionContract.pause();
        log.info("pause 结果: {}", receipt);
    }

    /**
     * 方法作用：解除暂停
     */
    @Test
    void testAionUnpause() throws Exception {
        var receipt = aionContract.unpause();
        log.info("unpause 结果: {}", receipt);
    }

    /**
     * 方法作用：设置社区地址
     */
    @Test
    void testAionSetCommunity() throws Exception {
        String newCommunity = requireTodoString("AION setCommunity 地址", "TODO:填写地址");
        var receipt = aionContract.setCommunity(newCommunity);
        log.info("setCommunity 结果: {}", receipt);
    }

    /**
     * 方法作用：设置兑换参数
     */
    @Test
    void testAionSetExchangeParams() throws Exception {
        boolean fixedEnabled = true; // TODO: true/false
        BigDecimal fixedAmountHuman = requireTodoAmount("AION fixedAmount", null);
        BigInteger fixedAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, fixedAmountHuman);
        BigInteger burnBps = requireTodoRaw("AION burnBps", null); // TODO: 例 8000
        BigInteger communityBps = requireTodoRaw("AION communityBps", null); // TODO: 例 2000
        var receipt = aionContract.setExchangeParams(fixedEnabled, fixedAmount, burnBps, communityBps);
        log.info("setExchangeParams 结果: {}", receipt);
    }

    // ===================== AionService ======================

    /**
     * 方法作用：查询可流通量
     */
    @Test
    void testAionCirculatingSupply() throws Exception {
        log.info("AION 可流通量: {}", aionService.queryCirculatingSupply());
    }

    /**
     * 方法作用：查询指定地址的兑换记录汇总
     */
    @Test
    void testAionExchangePaidSummary() throws Exception {
        String user = requireTodoString("AION ExchangePaid 查询地址", "TODO:填写地址");
        var summary = aionService.queryExchangePaidSummary(user);
        log.info("ExchangePaid 汇总: {}", summary);
    }

    // ===================== PaymentUsdtContract（只读）=====================

    /**
     * 方法作用：查询 PaymentUSDT 合约基础信息
     */
    @Test
    void testPaymentUsdtMeta() throws Exception {
        log.info("PaymentUSDT 地址: {}", paymentUsdtContract.getAddress());
        log.info("USDT 合约地址: {}", paymentUsdtContract.usdtAddress());
        log.info("管理员地址: {}", paymentUsdtContract.adminAddress());
        log.info("执行者地址: {}", paymentUsdtContract.executorAddress());
        log.info("收款地址: {}", paymentUsdtContract.treasuryAddress());
        log.info("最小扣款金额: {}", paymentUsdtContract.minAmount());
    }

    /**
     * 方法作用：查询订单号是否已执行
     */
    @Test
    void testPaymentUsdtExecuted() throws Exception {
        String orderIdHex = requireTodoString("PaymentUSDT 订单号", "TODO:填写订单ID(0x...)");
        log.info("订单执行状态: {}", paymentUsdtContract.executed(orderIdHex));
    }

    /**
     * 方法作用：查询授权额度（owner -> spender）
     */
    @Test
    void testPaymentUsdtAllowance() throws Exception {
        String owner = "0x75150a5dd1d992BEd89F49A15b6A70f43A5c8C64";
        String spender = "0x600F50058c8CEb5aD9448C82Ea3135D4C5539B12";
        log.info("授权额度: {}", paymentUsdtContract.allowance(owner, spender));
    }

    /**
     * 方法作用：查询授权额度（owner -> 合约）
     */
    @Test
    void testPaymentUsdtAllowanceToContract() throws Exception {
        String owner = "0x75150a5dd1d992BEd89F49A15b6A70f43A5c8C64";
        log.info("授权额度(合约): {}", paymentUsdtContract.allowanceToPaymentUsdt(owner));
    }

    /**
     * 方法作用：查询指定地址 USDT 余额
     */
    @Test
    void testPaymentUsdtBalanceOf() throws Exception {
        String owner = requireTodoString("PaymentUSDT balanceOf 地址", "TODO:填写地址");
        log.info("USDT 余额: {}", paymentUsdtContract.balanceOf(owner));
    }

    // ===================== PaymentUsdtContract（写操作）=====================

    /**
     * 方法作用：扣款测试
     */
    @Test
    void testPaymentUsdtDeposit() throws Exception {
        String user = requireTodoString("PaymentUSDT 扣款用户", "TODO:填写地址");
        BigDecimal amountHuman = requireTodoAmount("USDT 扣款金额", null);
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amountHuman);
        String orderIdHex = generateOrderIdHex();
        log.info("开始扣款，user: {}, amount(raw): {}, orderId: {}", user, amount, orderIdHex);
        var receipt = paymentUsdtContract.deposit(orderIdHex, user, amount);
        log.info("扣款结果: {}", receipt);
    }

    /**
     * 方法作用：设置收款地址
     */
    @Test
    void testPaymentUsdtSetTreasury() throws Exception {
        String newTreasury = requireTodoString("PaymentUSDT setTreasury 地址", "TODO:填写地址");
        var receipt = paymentUsdtContract.setTreasury(newTreasury);
        log.info("setTreasury 结果: {}", receipt);
    }

    /**
     * 方法作用：设置执行者地址
     */
    @Test
    void testPaymentUsdtSetExecutor() throws Exception {
        String newExecutor = requireTodoString("PaymentUSDT setExecutor 地址", "TODO:填写地址");
        var receipt = paymentUsdtContract.setExecutor(newExecutor);
        log.info("setExecutor 结果: {}", receipt);
    }

    /**
     * 方法作用：设置管理员地址
     */
    @Test
    void testPaymentUsdtSetAdmin() throws Exception {
        String newAdmin = requireTodoString("PaymentUSDT setAdmin 地址", "TODO:填写地址");
        var receipt = paymentUsdtContract.setAdmin(newAdmin);
        log.info("setAdmin 结果: {}", receipt);
    }

    /**
     * 方法作用：设置最小扣款金额
     */
    @Test
    void testPaymentUsdtSetMinAmount() throws Exception {
        BigInteger amount = new BigInteger(requireTodoString("PaymentUSDT setMinAmount", "TODO:填写最小金额（最小单位）"));
        var receipt = paymentUsdtContract.setMinAmount(amount);
        log.info("setMinAmount 结果: {}", receipt);
    }

    /**
     * 方法作用：暂停合约
     */
    @Test
    void testPaymentUsdtPause() throws Exception {
        var receipt = paymentUsdtContract.pause();
        log.info("pause 结果: {}", receipt);
    }

    /**
     * 方法作用：解除暂停
     */
    @Test
    void testPaymentUsdtUnpause() throws Exception {
        var receipt = paymentUsdtContract.unpause();
        log.info("unpause 结果: {}", receipt);
    }

    // ===================== CardNftContract（只读）=====================

    /**
     * 方法作用：查询卡牌 ID
     */
    @Test
    void testCardNftCardId() throws Exception {
        log.info("CardNFT CARD_ID: {}", cardNftContract.cardId());
    }

    /**
     * 方法作用：查询最大供应量
     */
    @Test
    void testCardNftMaxSupply() throws Exception {
        log.info("CardNFT MAX_SUPPLY: {}", cardNftContract.maxSupply());
    }

    /**
     * 方法作用：查询名称
     */
    @Test
    void testCardNftName() throws Exception {
        log.info("CardNFT name: {}", cardNftContract.name());
    }

    /**
     * 方法作用：查询符号
     */
    @Test
    void testCardNftSymbol() throws Exception {
        log.info("CardNFT symbol: {}", cardNftContract.symbol());
    }

    /**
     * 方法作用：查询用户余额
     */
    @Test
    void testCardNftBalanceOf() throws Exception {
        String user = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        log.info("CardNFT balanceOf({}): {}", user, cardNftContract.balanceOf(user));
    }

    /**
     * 方法作用：查询用户累计销毁数量
     */
    @Test
    void testCardNftBurnedAmount() throws Exception {
        String user = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        log.info("CardNFT burnedAmount({}): {}", user, cardNftContract.burnedAmount(user));
    }

    /**
     * 方法作用：查询历史已分发数量
     */
    @Test
    void testCardNftTotalMinted() throws Exception {
        log.info("CardNFT totalMinted: {}", cardNftContract.totalMinted());
    }

    /**
     * 方法作用：查询当前仍存在数量
     */
    @Test
    void testCardNftTotalSupply() throws Exception {
        log.info("CardNFT totalSupply: {}", cardNftContract.totalSupply());
    }

    /**
     * 方法作用：查询剩余未分发数量
     */
    @Test
    void testCardNftRemainingMintable() throws Exception {
        log.info("CardNFT remainingMintable: {}", cardNftContract.remainingMintable());
    }

    /**
     * 方法作用：查询是否已授权管理员
     */
    @Test
    void testCardNftIsApprovedForAll() throws Exception {
        String user = requireTodoString("CardNFT isApprovedForAll user", "TODO:填写地址");
        String admin = requireTodoString("CardNFT isApprovedForAll admin", "TODO:填写地址");
        log.info("CardNFT isApprovedForAll({}, {}): {}", user, admin, cardNftContract.isApprovedForAll(user, admin));
    }

    // ===================== CardNftContract（写操作）=====================

    /**
     * 方法作用：分发 NFT
     */
    @Test
    void testCardNftDistribute() throws Exception {
        String to = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        BigInteger amount = BigInteger.TEN;
        var receipt = cardNftContract.distribute(to, amount);
        log.info("distribute 结果: {}", receipt);
    }

    /**
     * 方法作用：销毁用户卡牌
     */
    @Test
    void testCardNftBurnUser() throws Exception {
        String user = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        BigInteger amount = BigInteger.TWO;
        var receipt = cardNftContract.burnUser(user, amount);
        log.info("burn 结果: {}", receipt);
    }

    /**
     * 方法作用：设置管理员
     */
    @Test
    void testCardNftSetAdmin() throws Exception {
        String admin = requireTodoString("CardNFT setAdmin 地址", "TODO:填写地址");
        boolean enabled = true; // TODO: true/false
        var receipt = cardNftContract.setAdmin(admin, enabled);
        log.info("setAdmin 结果: {}", receipt);
    }

    /**
     * 方法作用：设置 URI
     */
    @Test
    void testCardNftSetUri() throws Exception {
        String uri = requireTodoString("CardNFT setURI", "TODO:填写URI");
        var receipt = cardNftContract.setUri(uri);
        log.info("setURI 结果: {}", receipt);
    }
}
