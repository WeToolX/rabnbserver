package com.ra.rabnbserver.exception;

import lombok.Getter;

/**
 * AION 合约异常（包含原始与解码信息）
 * @author qiexi
 */
@Getter
public class AionContractException extends IllegalStateException {

    /**
     * 原始 revert 数据
     * -- GETTER --
     *  获取原始 revert 数据
     *

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
     *

     */
    private final String errorMessage;

    public AionContractException(String message, String rawReason, Integer errorCode, String errorName, String errorMessage) {
        super(message);
        this.rawReason = rawReason;
        this.errorCode = errorCode;
        this.errorName = errorName;
        this.errorMessage = errorMessage;
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
