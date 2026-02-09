package com.ra.rabnbserver.exception;

/**
 * AION 合约异常（BizError/Error(string) 解码）
 * @author qiexi
 */
public class AionContractException extends ContractCallException {

    public AionContractException(
            String message,
            String contractAddress,
            String functionName,
            String fromAddress,
            String callData,
            String rawReason,
            Integer errorCode,
            String errorName,
            String errorMessage
    ) {
        super(message, contractAddress, functionName, fromAddress, callData, rawReason, errorCode, errorName, errorMessage, null);
    }
}
