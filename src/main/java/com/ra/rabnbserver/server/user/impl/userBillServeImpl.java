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
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.card.EtfCardServe;
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

    private final EtfCardServe etfCardServe;


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

        // 锁定当前用户（核心安全条件）
        wrapper.eq(UserBill::getUserId, userId);

        // 动态筛选条件
        if (query.getBillType() != null) {
            wrapper.eq(UserBill::getBillType, query.getBillType());
        }
        if (query.getTransactionType() != null) {
            wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        }
        if (query.getFundType() != null) {
            wrapper.eq(UserBill::getFundType, query.getFundType());
        }
        // 交易状态筛选
        if (query.getTransactionStatus() != null) {
            wrapper.eq(UserBill::getStatus, query.getTransactionStatus());
        }

        // 时间范围筛选
        if (StringUtils.isNotBlank(query.getStartDate())) {
            // 使用 Hutool DateUtil 解析并转为当天开始时间 00:00:00
            LocalDateTime start = DateUtil.parse(query.getStartDate()).toLocalDateTime()
                    .with(LocalTime.MIN);
            wrapper.ge(UserBill::getTransactionTime, start);
        }

        if (StringUtils.isNotBlank(query.getEndDate())) {
            // 使用 Hutool DateUtil 解析并转为当天结束时间 23:59:59
            LocalDateTime end = DateUtil.parse(query.getEndDate()).toLocalDateTime()
                    .with(LocalTime.MAX);
            wrapper.le(UserBill::getTransactionTime, end);
        }

        // 排序：通常账单按交易时间倒序，时间相同按 ID 倒序
        wrapper.orderByDesc(UserBill::getTransactionTime)
                .orderByDesc(UserBill::getId);

        // 执行分页查询 (对传入的 page 和 size 做基本校验)
        long current = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        long size = (query.getSize() == null || query.getSize() < 1) ? 10 : query.getSize();

        return this.page(new Page<>(current, size), wrapper);
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

            String receiptJson = (receipt == null) ? null : JSON.toJSONString(receipt);


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
                            receiptJson
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
        // 1. 基本参数校验
        if (quantity <= 0) {
            throw new BusinessException("购买数量必须大于0");
        }

        User user = userMapper.selectById(userId);
        if (user == null || StringUtils.isBlank(user.getUserWalletAddress())) {
            throw new BusinessException("用户不存在或未绑定钱包");
        }

        // 2. 获取当前激活且启用的批次 (不再硬编码单价)
        ETFCard currentBatch = etfCardServe.getActiveAndEnabledBatch();
        if (currentBatch == null) {
            throw new BusinessException("购买失败：当前没有可售卖的卡牌批次");
        }

        // 3. 校验库存初步判断 (减少不必要的数据库锁竞争)
        if (currentBatch.getInventory() < quantity) {
            throw new BusinessException("库存不足，当前ETF仅剩: " + currentBatch.getInventory());
        }

        // 4. 计算总价
        BigDecimal totalCost = currentBatch.getUnitPrice().multiply(new BigDecimal(quantity));
        String orderId = "BILL_" + IdWorker.getIdStr();

        // 5. 执行编程式事务
        transactionTemplate.execute(status -> {
            try {
                // A. 【乐观锁扣减库存】
                // 核心：在 SQL 层面增加 inventory >= quantity 的判断，利用数据库行锁保证并发安全
                boolean inventoryUpdated = etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                        .eq(ETFCard::getId, currentBatch.getId())
                        .ge(ETFCard::getInventory, quantity) // 关键：乐观锁条件，确保库存够扣
                        .setSql("inventory = inventory - " + quantity)
                        .setSql("sold_count = sold_count + " + quantity));

                if (!inventoryUpdated) {
                    // 如果返回 false，说明在该事务执行期间，库存已被其他线程抢先扣减
                    throw new BusinessException("抢购失败：库存已被抢光或不足");
                }
                // B. 执行平台余额扣款
                this.createBillAndUpdateBalance(
                        userId,
                        totalCost,
                        BillType.PLATFORM,
                        FundType.EXPENSE,
                        TransactionType.PURCHASE,
                        "购买NFT卡牌(" + currentBatch.getBatchNo() + ") x" + quantity,
                        orderId,
                        null,
                        null
                );

                // C. 执行链上分发（distribute）
                log.info("开始链上分发: 批次={}, 用户={}, 数量={}", currentBatch.getBatchNo(), user.getUserWalletAddress(), quantity);
                TransactionReceipt receipt = cardNftContract.distribute(
                        user.getUserWalletAddress(),
                        BigInteger.valueOf(quantity)
                );

                // D. 检查合约执行状态并补全账单
                if (receipt != null && "0x1".equals(receipt.getStatus())) {
                    String receiptJson = JSON.toJSONString(receipt);
                    String txHash = receipt.getTransactionHash();
                    boolean billUpdated = this.update(new LambdaUpdateWrapper<UserBill>()
                            .eq(UserBill::getTransactionOrderId, orderId)
                            .set(UserBill::getTxId, txHash)
                            .set(UserBill::getChainResponse, receiptJson));
                    if (!billUpdated) {
                        throw new BusinessException("账单同步失败");
                    }
                    log.info("购买成功: 订单={}, txHash={}", orderId, txHash);
                    return true;
                } else {
                    log.error("合约执行失败: {}", receipt);
                    throw new BusinessException("链上铸造失败，资金已退回");
                }

            } catch (BusinessException e) {
                status.setRollbackOnly(); // 触发事务回滚：库存会加回去，余额扣款会撤销
                throw e;
            } catch (Exception e) {
                log.error("购买过程发生异常: ", e);
                status.setRollbackOnly();
                throw new BusinessException("系统处理异常: " + e.getMessage());
            }
        });
    }
}
