package com.ra.rabnbserver.contract;

import lombok.extern.slf4j.Slf4j;
import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.addressArray;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256Array;

/**
 * CardNFT 合约管理员调用接口
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class CardNftContract extends ContractBase {

    private final ContractAddressProperties contractAddressProperties;

    public CardNftContract(Web3j web3j, TransactionManager transactionManager, BlockchainProperties blockchainProperties,
                           ContractAddressProperties contractAddressProperties) {
        super(web3j, transactionManager, blockchainProperties);
        this.contractAddressProperties = contractAddressProperties;
    }

    /**
     * 单发卡
     *
     * @param to 接收地址
     * @param amount 数量
     * @return 交易回执
     */
    public TransactionReceipt mint(String to, BigInteger amount) throws Exception {
        Function function = new Function(
                "mint",
                List.of(address(to), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 批量发卡
     *
     * @param toList 接收地址列表
     * @param amountList 数量列表
     * @return 交易回执
     */
    public TransactionReceipt mintBatch(List<String> toList, List<BigInteger> amountList) throws Exception {
        Function function = new Function(
                "mintBatch",
                List.of(addressArray(toList), uint256Array(amountList)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 管理员销毁指定用户卡牌
     *
     * @param from 用户地址
     * @param amount 数量
     * @return 交易回执
     */
    public TransactionReceipt adminBurn(String from, BigInteger amount) throws Exception {
        Function function = new Function(
                "adminBurn",
                List.of(address(from), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置管理员
     *
     * @param newAdmin 新管理员地址
     * @return 交易回执
     */
    public TransactionReceipt setAdmin(String newAdmin) throws Exception {
        Function function = new Function(
                "setAdmin",
                List.of(address(newAdmin)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 暂停合约
     *
     * @return 交易回执
     */
    public TransactionReceipt pause() throws Exception {
        Function function = new Function("pause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 解除暂停
     *
     * @return 交易回执
     */
    public TransactionReceipt unpause() throws Exception {
        Function function = new Function("unpause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 查询是否暂停
     *
     * @return 是否暂停
     */
    public Boolean paused() throws Exception {
        Function function = buildViewFunction("paused", List.of(new TypeReference<Bool>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.get(0).getValue();
    }

    /**
     * 查询已铸造数量
     *
     * @return 已铸造数量
     */
    public BigInteger totalMinted() throws Exception {
        Function function = buildViewFunction("totalMinted", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 获取合约地址
     *
     * @return 合约地址
     */
    public String getAddress() {
        return contractAddressProperties.getCardNft();
    }
}
