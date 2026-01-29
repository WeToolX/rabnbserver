package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionStatus;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.user.userBillServe;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class userBillServeImpl extends ServiceImpl<UserBillMapper, UserBill> implements userBillServe {

    private final UserMapper userMapper;

    // 1. 注入事务模板
    private final TransactionTemplate transactionTemplate;


    /**
     * 统一创建账单并同步更新用户展示余额
     * @param userId  用户id
     * @param amount 操作金额
     * @param billType  账本类型
     * @param fundType  资金类型
     * @param txType 交易类型
     * @param remark 备注
     * @param orderId 订单id
     * @param txId   交易哈希
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType,
                                           FundType fundType, TransactionType txType,
                                           String remark, String orderId, String txId,String res) {


        //默认值处理
        if (StringUtils.isBlank(orderId)) {
            orderId = "BILL_" + IdWorker.getIdStr();
        }
        // 如果 remark 为空，默认使用交易类型的描述
        if (StringUtils.isBlank(remark)) {
            remark = txType.getDesc();
        }

        // 1. 悲观锁锁定用户，确保流水计算的串行化
        // 即便不更新用户余额，锁定用户也是为了防止该用户的多条流水（同类型）并发插入导致余额计算错乱
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 2. 动态获取该用户【当前账单类型】的上一笔最后余额
        // 逻辑：如果是平台账单查平台余额，如果是链上账单查链上余额
        BigDecimal balanceBefore = BigDecimal.ZERO;
        UserBill lastBill = this.getOne(new LambdaQueryWrapper<UserBill>()
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getBillType, billType) // 这里改为动态匹配传入的 billType
                .orderByDesc(UserBill::getId)
                .last("LIMIT 1"));

        if (lastBill != null) {
            balanceBefore = lastBill.getBalanceAfter();
        }

        // 3. 计算金额变动
        BigDecimal changeAmount = amount;
        if (FundType.EXPENSE.equals(fundType)) {
            changeAmount = amount.negate();
        }

        // 4. 计算变动后的余额
        BigDecimal balanceAfter = balanceBefore.add(changeAmount);

        // 5. 余额合法性校验（通常平台账单和链上账单都不允许余额小于0）
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(billType.getDesc() + "账户余额不足");
        }

        // 6. 插入新账单
        UserBill newBill = new UserBill();
        newBill.setUserId(userId);
        newBill.setUserWalletAddress(user.getUserWalletAddress());
        newBill.setTransactionOrderId(orderId);
        newBill.setTxId(txId);
        newBill.setBillType(billType);
        newBill.setFundType(fundType);
        newBill.setTransactionType(txType);
        newBill.setAmount(amount);
        newBill.setBalanceBefore(balanceBefore);
        newBill.setBalanceAfter(balanceAfter);
        newBill.setRemark(remark);
        newBill.setTransactionTime(LocalDateTime.now());
        newBill.setStatus(TransactionStatus.SUCCESS);
        newBill.setChainResponse(res);
        this.save(newBill);

        // 7. 同步更新用户表的余额字段（仅针对 PLATFORM 类型）
        if (BillType.PLATFORM.equals(billType)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getBalance, balanceAfter));
        }

        log.info("账单记录成功: 类型={}, 用户={}, 变动前={}, 变动后={}",
                billType, userId, balanceBefore, balanceAfter);
    }

    @Override
    public IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<>();

        // 1. 锁定当前用户
        wrapper.eq(UserBill::getUserId, userId);

        // 2. 账单类型和交易类型筛选
        if (query.getBillType() != null) {
            wrapper.eq(UserBill::getBillType, query.getBillType());
        }
        if (query.getTransactionType() != null) {
            wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        }
        if (query.getFundType() != null) {
            wrapper.eq(UserBill::getFundType, query.getFundType());
        }


        if (StringUtils.isNotBlank(query.getStartDate())) {
            LocalDateTime start = DateUtil.parse(query.getStartDate()).toLocalDateTime()
                    .with(LocalTime.MIN);
            wrapper.ge(UserBill::getTransactionTime, start);
        }

        if (StringUtils.isNotBlank(query.getEndDate())) {
            LocalDateTime end = DateUtil.parse(query.getEndDate()).toLocalDateTime()
                    .with(LocalTime.MAX);
            wrapper.le(UserBill::getTransactionTime, end);
        }
        // 4. 按时间倒序排序
        wrapper.orderByDesc(UserBill::getId);
        // 5. 执行分页查询
        return this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    private final PaymentUsdtContract paymentUsdtContract;


    /**
     * 充值接口链上扣款用户资金方法
     * @param userId
     * @param amount
     */
    @Override
    public void rechargeFromChain(Long userId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null || StringUtils.isBlank(user.getUserWalletAddress())) {
            throw new BusinessException("用户不存在或未绑定钱包");
        }
        String walletAddress = user.getUserWalletAddress();

        //BigInteger chainAmount = amount.multiply(BigDecimal.valueOf(1_000_000L)).toBigInteger();//
        BigInteger  chainAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amount);
        String orderIdHex = "0x" + IdWorker.get32UUID(); // 生成32位唯一订单十六进制串

        try {
            BigInteger allowance = paymentUsdtContract.allowanceToPaymentUsdt(walletAddress);
            if (allowance.compareTo(chainAmount) < 0) {
                throw new BusinessException("用户授权额度不足，请先在钱包授权");
            }
            BigInteger balance = paymentUsdtContract.balanceOf(walletAddress);
            if (balance.compareTo(chainAmount) < 0) {
                throw new BusinessException("用户链上USDT余额不足");
            }

            log.info("开始执行链上扣款: 用户={}, 金额={}, 订单={}", walletAddress, amount, orderIdHex);
            TransactionReceipt receipt = paymentUsdtContract.deposit(orderIdHex, walletAddress, chainAmount);



            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                transactionTemplate.execute(status -> {
                    this.createBillAndUpdateBalance(
                            userId,
                            amount,
                            BillType.PLATFORM,
                            FundType.INCOME,
                            TransactionType.DEPOSIT,
                            "USDT充值成功",
                            orderIdHex,
                            receipt.getTransactionHash(),
                            receipt.toString()
                    );
                    return null;
                });

                log.info("链上充值成功入账: 用户={}, TxHash={}", userId, receipt.getTransactionHash());
            } else {
                // 失败：记录异常账单（不更新用户余额）
                saveExceptionBill(user, amount, orderIdHex, receipt != null ? receipt.getTransactionHash() : null, "合约执行失败");
                throw new BusinessException("链上交易执行失败，请检查区块状态");
            }

        } catch (Exception e) {
            log.error("链上充值异常: ", e);
            // 如果是业务异常直接抛出
            if (e instanceof BusinessException) throw (BusinessException) e;

            // 其他未知异常（如网络超时），记录一条状态为失败的账单
            saveExceptionBill(user, amount, orderIdHex, null, "系统处理异常: " + e.getMessage());
            throw new BusinessException("充值请求异常: " + e.getMessage());
        }
    }

    /**
     * 辅助方法：记录失败/异常账单
     */
    private void saveExceptionBill(User user, BigDecimal amount, String orderId, String txHash, String errorMsg) {
        UserBill failBill = new UserBill();
        failBill.setUserId(user.getId());
        failBill.setUserWalletAddress(user.getUserWalletAddress());
        failBill.setTransactionOrderId(orderId);
        failBill.setTxId(txHash);
        failBill.setBillType(BillType.ERROR_ORDER);
        failBill.setFundType(FundType.INCOME);
        failBill.setTransactionType(TransactionType.DEPOSIT);
        failBill.setAmount(amount);
        failBill.setBalanceBefore(user.getBalance());
        failBill.setBalanceAfter(user.getBalance());
        failBill.setRemark("账单异常: " + errorMsg);
        failBill.setTransactionTime(LocalDateTime.now());
        failBill.setStatus(TransactionStatus.FAILED); // 状态设为失败/异常
        this.save(failBill);
    }


    // 在 userBillServeImpl 中注入 CardNftContract
    private final CardNftContract cardNftContract;

    // 定义 NFT 单价（常量或从配置中读取）
    private static final BigDecimal NFT_UNIT_PRICE = new BigDecimal("1");

    @Override
    public void purchaseNftCard(Long userId, int quantity) {
        // 参数校验
        if (quantity <= 0) {
            throw new BusinessException("购买数量必须大于0");
        }
        User user = userMapper.selectById(userId);
        if (user == null || StringUtils.isBlank(user.getUserWalletAddress())) {
            throw new BusinessException("用户不存在或未绑定钱包");
        }
        // 计算总价
        BigDecimal totalCost = NFT_UNIT_PRICE.multiply(new BigDecimal(quantity));
        // 预生成订单号，用于后续更新
        String orderId = "BILL_" + IdWorker.getIdStr();

        // 执行编程式事务
        transactionTemplate.execute(status -> {
            try {
                // 执行统一扣款逻辑 (此时 txId 和 res 为空)
                // 该方法内部会锁定用户、计算余额并插入一条 status 为 SUCCESS 的流水
                this.createBillAndUpdateBalance(
                        userId,
                        totalCost,
                        BillType.PLATFORM,
                        FundType.EXPENSE,
                        TransactionType.PURCHASE,
                        "购买NFT卡牌 x" + quantity,
                        orderId,
                        null, // txId 暂空
                        null  // res 暂空
                );
                // 调用链上 Mint 方法
                log.info("开始链上铸造 NFT: 用户={}, 数量={}, 订单={}", user.getUserWalletAddress(), quantity, orderId);
                TransactionReceipt receipt = cardNftContract.mint(
                        user.getUserWalletAddress(),
                        BigInteger.valueOf(quantity)
                );

                // 检查合约执行状态
                if (receipt != null && "0x1".equals(receipt.getStatus())) {
                    String receiptJson = JSON.toJSONString(receipt);
                    String txHash = receipt.getTransactionHash();
                    // 更新刚才创建的账单记录，补全 txId 和 chainResponse
                    boolean updateResult = this.update(new LambdaUpdateWrapper<UserBill>()
                            .eq(UserBill::getTransactionOrderId, orderId)
                            .set(UserBill::getTxId, txHash)
                            .set(UserBill::getChainResponse, receiptJson));
                    if (!updateResult) {
                        log.error("补全账单信息失败: orderId={}", orderId);
                        throw new BusinessException("账单系统同步异常");
                    }
                    log.info("NFT 购买成功并已补全账单: txHash={}", txHash);
                    return true;
                } else {
                    // 回执显示失败，抛出异常触发回滚（撤销扣款）
                    log.error("合约 Mint 失败或回执为空: {}", receipt);
                    throw new BusinessException("链上铸造失败，资金已自动退回");
                }
            } catch (BusinessException e) {
                status.setRollbackOnly(); // 显式标记回滚
                throw e;
            } catch (Exception e) {
                log.error("购买过程发生未知异常，执行事务回滚: ", e);
                status.setRollbackOnly();// 显式标记回滚
                throw new BusinessException("购买服务异常: " + e.getMessage());
            }
        });
    }
}