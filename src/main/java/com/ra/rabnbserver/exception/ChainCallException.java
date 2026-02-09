package com.ra.rabnbserver.exception;

/**
 * 链上调用异常（RPC/签名/余额/网络等非 revert 错误）
 * @author qiexi
 */
public class ChainCallException extends ContractCallException {

    public ChainCallException(
            String message,
            String contractAddress,
            String functionName,
            String fromAddress,
            String callData,
            String rawReason,
            Throwable cause
    ) {
        super(message, contractAddress, functionName, fromAddress, callData, rawReason, null, null, null, cause);
    }
}
