package com.ra.rabnbserver.exception;

import lombok.Getter;

/**
 * 合约调用异常基类（包含原始与解码信息）
 * @author qiexi
 */
@Getter
public class ContractCallException extends IllegalStateException {

    /**
     * 合约地址
     * -- GETTER --
     *  获取合约地址
     */
    private final String contractAddress;
    /**
     * 方法名
     * -- GETTER --
     *  获取方法名
     */
    private final String functionName;
    /**
     * 发起地址
     * -- GETTER --
     *  获取发起地址
     */
    private final String fromAddress;
    /**
     * 调用数据
     * -- GETTER --
     *  获取调用数据
     */
    private final String callData;
    /**
     * 原始 revert 数据
     * -- GETTER --
     *  获取原始 revert 数据
     */
    private final String rawReason;
    /**
     * 业务错误码（BizError 的 code）
     * -- GETTER --
     *  获取业务错误码
     */
    private final Integer errorCode;
    /**
     * 业务错误名（BizError 的 name 或 Error(string)）
     * -- GETTER --
     *  获取业务错误名
     */
    private final String errorName;
    /**
     * 解码后的错误描述
     * -- GETTER --
     *  获取错误描述
     */
    private final String errorMessage;
    /**
     * 原始异常类型
     * -- GETTER --
     *  获取原始异常类型
     */
    private final String originExceptionClass;
    /**
     * 原始异常消息
     * -- GETTER --
     *  获取原始异常消息
     */
    private final String originExceptionMessage;

    public ContractCallException(
            String message,
            String contractAddress,
            String functionName,
            String fromAddress,
            String callData,
            String rawReason,
            Integer errorCode,
            String errorName,
            String errorMessage,
            Throwable cause
    ) {
        super(message, cause);
        this.contractAddress = contractAddress;
        this.functionName = functionName;
        this.fromAddress = fromAddress;
        this.callData = callData;
        this.rawReason = rawReason;
        this.errorCode = errorCode;
        this.errorName = errorName;
        this.errorMessage = errorMessage;
        this.originExceptionClass = cause == null ? null : cause.getClass().getName();
        this.originExceptionMessage = cause == null ? null : cause.getMessage();
    }

    /**
     * 获取解码摘要（用于日志/前端展示）
     *
     * @return 解码摘要
     */
    public String getDecodedSummary() {
        if (errorName == null || errorName.isBlank()) {
            return null;
        }
        if (errorCode == null) {
            return errorName;
        }
        return errorName + "(" + errorCode + ")";
    }

    /**
     * 获取解码详情（包含错误名、错误码与描述）
     *
     * @return 解码详情
     */
    public String getDecodedDetail() {
        if (errorName == null || errorName.isBlank()) {
            return errorMessage;
        }
        if (errorCode == null) {
            return errorName + ": " + errorMessage;
        }
        return errorName + "(" + errorCode + "): " + errorMessage;
    }
}
