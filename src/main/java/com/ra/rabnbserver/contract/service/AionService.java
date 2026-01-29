package com.ra.rabnbserver.contract.service;

import com.ra.rabnbserver.contract.AionContract;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * AION 合约业务服务
 */
@Service
public class AionService {

    /**
     * ExchangePaid 事件名
     */
    private static final String EVENT_EXCHANGE_PAID = "ExchangePaid";

    /**
     * 部署区块高度
     */
    private static final BigInteger DEPLOY_BLOCK = BigInteger.valueOf(87_040_866L);

    private final Web3j web3j;
    private final AionContract aionContract;

    public AionService(Web3j web3j, AionContract aionContract) {
        this.web3j = web3j;
        this.aionContract = aionContract;
    }

    /**
     * 查询当前可流通量
     *
     * 计算公式：totalSupply - balanceOf(address(this))
     *
     * @return 可流通量
     *         返回类型：BigInteger
     *         JSON 序列化示例：1000000
     *         含义：可流通量（链上原始数量），可能为 null（RPC 未返回）
     */
    public BigInteger queryCirculatingSupply() throws Exception {
        // 1. totalSupply
        BigInteger totalSupply = aionContract.totalSupply();
        // 2. 锁仓量 = 合约自身余额
        String contractAddr = aionContract.getAddress();
        BigInteger locked = aionContract.balanceOf(contractAddr);
        // 3. 可流通量
        if (totalSupply == null || locked == null) {
            return null;
        }
        return totalSupply.subtract(locked);
    }

    /**
     * 查询指定地址的兑换记录汇总
     *
     * @param user 用户地址
     * @return 兑换记录汇总
     *         返回类型：ExchangePaidSummary（JSON 对象）
     *         JSON 示例：
     *         {
     *           "user": "0x...",
     *           "totalCount": 2,
     *           "totalPaid": 1000000,
     *           "totalBurned": 500000,
     *           "totalToCommunity": 500000,
     *           "records": [
     *             {
     *               "blockNumber": 87040870,
     *               "txHash": "0x...",
     *               "user": "0x...",
     *               "paidAmount": 1000000,
     *               "burnedAmount": 500000,
     *               "toCommunityAmount": 500000
     *             }
     *           ]
     *         }
     */
    public ExchangePaidSummary queryExchangePaidSummary(String user) throws Exception {
        Event event = new Event(
                EVENT_EXCHANGE_PAID,
                List.of(
                        new TypeReference<Address>(true) {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {}
                )
        );

        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(DEPLOY_BLOCK),
                DefaultBlockParameterName.LATEST,
                aionContract.getAddress()
        );
        filter.addSingleTopic(EventEncoder.encode(event));
        if (user != null && !user.isBlank()) {
            filter.addSingleTopic(toTopicAddress(user));
        }

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        List<ExchangePaidRecord> records = new ArrayList<>();
        BigInteger totalPaid = BigInteger.ZERO;
        BigInteger totalBurned = BigInteger.ZERO;
        BigInteger totalToCommunity = BigInteger.ZERO;

        for (EthLog.LogResult<?> logResult : ethLog.getLogs()) {
            Log logItem = (Log) logResult.get();
            List<Type> decoded = FunctionReturnDecoder.decode(logItem.getData(), event.getNonIndexedParameters());
            if (decoded.size() < 3) {
                continue;
            }
            BigInteger paidAmount = (BigInteger) decoded.get(0).getValue();
            BigInteger burnedAmount = (BigInteger) decoded.get(1).getValue();
            BigInteger toCommunityAmount = (BigInteger) decoded.get(2).getValue();
            String eventUser = extractIndexedAddress(logItem);

            ExchangePaidRecord record = new ExchangePaidRecord();
            record.setBlockNumber(logItem.getBlockNumber() == null ? null : logItem.getBlockNumber().longValue());
            record.setTxHash(logItem.getTransactionHash());
            record.setUser(eventUser);
            record.setPaidAmount(paidAmount);
            record.setBurnedAmount(burnedAmount);
            record.setToCommunityAmount(toCommunityAmount);
            records.add(record);

            totalPaid = totalPaid.add(paidAmount);
            totalBurned = totalBurned.add(burnedAmount);
            totalToCommunity = totalToCommunity.add(toCommunityAmount);
        }

        ExchangePaidSummary summary = new ExchangePaidSummary();
        summary.setUser(user);
        summary.setTotalCount(records.size());
        summary.setTotalPaid(totalPaid);
        summary.setTotalBurned(totalBurned);
        summary.setTotalToCommunity(totalToCommunity);
        summary.setRecords(records);
        return summary;
    }

    /**
     * 提取 indexed 的 user 地址
     *
     * @param logItem 日志
     * @return 地址
     */
    private String extractIndexedAddress(Log logItem) {
        if (logItem.getTopics() == null || logItem.getTopics().size() < 2) {
            return null;
        }
        String topic = logItem.getTopics().get(1);
        String clean = Numeric.cleanHexPrefix(topic);
        if (clean.length() < 40) {
            return null;
        }
        String addr = clean.substring(clean.length() - 40);
        return "0x" + addr;
    }

    /**
     * 将地址转换为事件 topic
     *
     * @param address 地址
     * @return topic
     */
    private String toTopicAddress(String address) {
        String clean = Numeric.cleanHexPrefix(address);
        return Numeric.toHexStringWithPrefixZeroPadded(new BigInteger(clean, 16), 64);
    }

    /**
     * ExchangePaid 事件记录
     */
    @Data
    public static class ExchangePaidRecord {
        /**
         * 区块高度
         */
        private Long blockNumber;

        /**
         * 交易哈希
         */
        private String txHash;

        /**
         * 用户地址
         */
        private String user;

        /**
         * 支付数量（最小单位）
         */
        private BigInteger paidAmount;

        /**
         * 销毁数量（最小单位）
         */
        private BigInteger burnedAmount;

        /**
         * 社区数量（最小单位）
         */
        private BigInteger toCommunityAmount;
    }

    /**
     * ExchangePaid 汇总
     */
    @Data
    public static class ExchangePaidSummary {
        /**
         * 用户地址
         */
        private String user;

        /**
         * 兑换次数
         */
        private Integer totalCount;

        /**
         * 总支付数量（最小单位）
         */
        private BigInteger totalPaid;

        /**
         * 总销毁数量（最小单位）
         */
        private BigInteger totalBurned;

        /**
         * 总社区数量（最小单位）
         */
        private BigInteger totalToCommunity;

        /**
         * 明细记录
         */
        private List<ExchangePaidRecord> records;
    }
}
