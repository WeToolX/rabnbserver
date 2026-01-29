package com.ra.rabnbserver.contract.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 金额转换工具（链上最小单位 -> 人类可读数值）
 */
public final class AmountConvertUtils {

    private AmountConvertUtils() {
    }

    /**
     * 币种类型（当前系统）
     */
    public enum Currency {
        /**
         * USDT
         */
        USDT,
        /**
         * AION
         */
        AION
    }

    /**
     * 将链上最小单位金额转换为人类可读金额
     *
     * @param currency 币种（USDT/AION）
     * @param rawAmount 未格式化的原始数额（链上最小单位）
     * @param decimals 小数精度位数
     * @return 人类可读金额（BigDecimal，已去掉末尾 0，截断不四舍五入）
     */
    public static BigDecimal toHumanAmount(Currency currency, BigInteger rawAmount, int decimals) {
        if (currency == null) {
            throw new IllegalArgumentException("币种不能为空");
        }
        if (rawAmount == null) {
            throw new IllegalArgumentException("原始数额不能为空");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("小数精度不能小于 0");
        }
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
        BigDecimal value = new BigDecimal(rawAmount).divide(divisor, decimals, RoundingMode.DOWN);
        return value.stripTrailingZeros();
    }
}
