package com.ra.rabnbserver.crypto;

/**
 * 加解密常量
 */
public final class CryptoConstants {

    /**
     * 业务盐值（保持与旧系统一致）
     */
    public static final String TOKEN_SALT = "456787415adfdfsdf";

    /**
     * 包裹结构长度定义
     */
    public static final int PREFIX_LEN = 5;   // 4B 随机 + 0x00
    public static final int SUFFIX_LEN = 5;   // 0x00 + 4B 随机
    public static final int IV_LEN = 16;      // AES IV 长度

    private CryptoConstants() {
    }
}
