package com.ra.rabnbserver.utils;

import java.security.SecureRandom;

/**
 * 随机 ID 生成器
 */
public final class RandomIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomIdGenerator() {
    }

    /**
     * 生成 16 字节随机数的十六进制字符串
     */
    public static String generateRandom16ByteHexString() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
