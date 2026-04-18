package com.ra.rabnbserver.server.miner.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Shared conversion helpers for miner profit amounts.
 * Database records keep the standard decimal amount, while chain calls use
 * a 3-decimal fixed-point integer representation.
 */
public final class MinerProfitAmountConverter {

    private static final int PROFIT_SCALE = 6;
    private static final int CHAIN_FIXED_SCALE = 3;

    private MinerProfitAmountConverter() {
    }

    public static BigDecimal normalizeProfitAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(PROFIT_SCALE, RoundingMode.DOWN);
        }
        return amount.setScale(PROFIT_SCALE, RoundingMode.DOWN);
    }

    public static BigInteger toChainAmount(BigDecimal amount) {
        return normalizeProfitAmount(amount)
                .movePointRight(CHAIN_FIXED_SCALE)
                .toBigInteger();
    }

    public static BigInteger toChainAmount(BigDecimal amount, int quantity) {
        if (quantity <= 0) {
            return BigInteger.ZERO;
        }
        return toChainAmount(amount).multiply(BigInteger.valueOf(quantity));
    }
}
