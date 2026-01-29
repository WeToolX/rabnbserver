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
        USDT(6),
        /**
         * AION
         */
        AION(18);

        /**
         * 小数精度位数
         */
        private final int decimals;

        Currency(int decimals) {
            this.decimals = decimals;
        }

        /**
         * 获取小数精度
         *
         * @return 小数精度
         */
        public int getDecimals() {
            return decimals;
        }
    }

    /**
     * 将链上最小单位金额转换为人类可读金额
     *
     * @param currency 币种（USDT/AION）
     * @param rawAmount 未格式化的原始数额（链上最小单位）
     * @param decimals 人类可读小数位数（用于截断显示）
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
        int currencyDecimals = currency.getDecimals();
        if (decimals > currencyDecimals) {
            throw new IllegalArgumentException("小数精度不能大于币种默认精度");
        }
        BigDecimal divisor = BigDecimal.TEN.pow(currencyDecimals);
        BigDecimal value = new BigDecimal(rawAmount).divide(divisor, currencyDecimals, RoundingMode.DOWN);
        BigDecimal truncated = value.setScale(decimals, RoundingMode.DOWN);
        return truncated.stripTrailingZeros();
    }

    /**
     * 将人类可读金额转换为链上最小单位
     *
     * @param currency 币种（USDT/AION）
     * @param humanAmount 人类可读金额
     * @return 链上最小单位金额（BigInteger，截断不四舍五入）
     */
    public static BigInteger toRawAmount(Currency currency, BigDecimal humanAmount) {
        if (currency == null) {
            throw new IllegalArgumentException("币种不能为空");
        }
        if (humanAmount == null) {
            throw new IllegalArgumentException("人类可读金额不能为空");
        }
        int decimals = currency.getDecimals();
        BigDecimal scaled = humanAmount.setScale(decimals, RoundingMode.DOWN);
        BigDecimal raw = scaled.movePointRight(decimals);
        return raw.toBigInteger();
    }
}
