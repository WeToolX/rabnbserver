package com.ra.rabnbserver.contract.support;

import com.ra.rabnbserver.exception.ChainCallException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

/**
 * 合约调用基座
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@RequiredArgsConstructor
public abstract class ContractBase {

    protected final Web3j web3j;
    protected final TransactionManager transactionManager;
    protected final BlockchainProperties blockchainProperties;

    /**
     * 发送交易（写操作）
     *
     * @param contractAddress 合约地址
     * @param function        调用函数
     * @return 交易回执
     */
    protected TransactionReceipt sendTransaction(String contractAddress, Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        BigInteger gasPrice = queryGasPrice();
        BigInteger gasLimit = estimateGas(contractAddress, data);
        EthSendTransaction sendResult = transactionManager.sendTransaction(gasPrice, gasLimit, contractAddress, data, BigInteger.ZERO);
        if (sendResult.hasError()) {
            String errorMessage = sendResult.getError().getMessage();
            log.error("合约交易发送失败: {}", errorMessage);
            String from = transactionManager.getFromAddress();
            throw new ChainCallException(errorMessage, contractAddress, function.getName(), from, data, errorMessage, null);
        }
        String txHash = sendResult.getTransactionHash();
        if (txHash == null || txHash.isBlank()) {
            log.error("合约交易发送失败: 未返回交易哈希");
            String from = transactionManager.getFromAddress();
            throw new ChainCallException("未返回交易哈希", contractAddress, function.getName(), from, data, null, null);
        }
        return waitForReceipt(txHash);
    }

    /**
     * 读取调用（只读）
     *
     * @param contractAddress 合约地址
     * @param function        调用函数
     * @return 解码后的返回值列表
     */
    protected List<Type> callFunction(String contractAddress, Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        String from = transactionManager.getFromAddress();
        Transaction transaction = Transaction.createEthCallTransaction(from, contractAddress, data);
        EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        if (response.isReverted()) {
            log.warn("合约只读调用回退: {}", response.getRevertReason());
        }
        return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    }

    /**
     * 读取单一返回值
     *
     * @param contractAddress 合约地址
     * @param function        调用函数
     * @param typeReference   返回值类型
     * @param <T>             类型
     * @return 返回值
     */
    protected <T extends Type> T callSingleValue(
            String contractAddress,
            Function function,
            TypeReference<T> typeReference
    ) throws Exception {
        List<Type> outputs = callFunction(contractAddress, function);
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T value = (T) outputs.get(0);
        return value;
    }

    /**
     * 获取 Gas Price
     *
     * @return Gas Price
     */
    private BigInteger queryGasPrice() throws Exception {
        EthGasPrice gasPrice = web3j.ethGasPrice().send();
        return gasPrice.getGasPrice();
    }

    /**
     * 估算 Gas Limit
     *
     * @param contractAddress 合约地址
     * @param data            ABI 数据
     * @return Gas Limit
     */
    private BigInteger estimateGas(String contractAddress, String data) throws Exception {
        String from = transactionManager.getFromAddress();
        Transaction transaction = Transaction.createFunctionCallTransaction(
                from,
                null,
                null,
                null,
                contractAddress,
                BigInteger.ZERO,
                data
        );
        EthEstimateGas estimateGas = web3j.ethEstimateGas(transaction).send();
        if (estimateGas.hasError()) {
            log.warn("Gas 估算失败，使用默认值: {}", estimateGas.getError().getMessage());
            return blockchainProperties.getGasLimitDefault();
        }
        BigInteger gasUsed = estimateGas.getAmountUsed();
        if (gasUsed == null || gasUsed.signum() <= 0) {
            return blockchainProperties.getGasLimitDefault();
        }
        // 预留 20% 余量
        return gasUsed.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    }

    /**
     * 轮询等待交易回执
     *
     * @param txHash 交易哈希
     * @return 回执
     */
    private TransactionReceipt waitForReceipt(String txHash) throws Exception {
        long pollIntervalMillis = blockchainProperties.getTxPollIntervalMs();
        long timeoutMillis = blockchainProperties.getTxTimeoutMs();
        int attempts = (int) Math.max(1L, timeoutMillis / pollIntervalMillis);
        TransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(web3j, pollIntervalMillis, attempts);
        return processor.waitForTransactionReceipt(txHash);
    }

    /**
     * 构造只读函数（无入参）
     *
     * @param name      函数名
     * @param outputs   返回类型
     * @return Function
     */
    protected Function buildViewFunction(String name, List<TypeReference<?>> outputs) {
        return new Function(name, Collections.emptyList(), outputs);
    }
}
