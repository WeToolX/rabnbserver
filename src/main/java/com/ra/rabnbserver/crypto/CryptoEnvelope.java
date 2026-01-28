package com.ra.rabnbserver.crypto;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 自定义加密包裹格式解析与封装
 * 结构: [4B随机][0x00][IV16][CIPHER][0x00][4B随机]
 */
public final class CryptoEnvelope {

    private CryptoEnvelope() {
    }

    /**
     * 解析 Base64 包裹
     */
    public static Parsed parse(String base64) {
        byte[] decoded = CryptoUtils.base64Decode(base64);
        validate(decoded);

        int ivStart = CryptoConstants.PREFIX_LEN;
        int ivEnd = ivStart + CryptoConstants.IV_LEN;
        int ctStart = ivEnd;
        int ctEnd = decoded.length - CryptoConstants.SUFFIX_LEN;

        byte[] iv = Arrays.copyOfRange(decoded, ivStart, ivEnd);
        byte[] cipher = Arrays.copyOfRange(decoded, ctStart, ctEnd);
        return new Parsed(iv, cipher);
    }

    /**
     * 封装为 Base64 包裹
     */
    public static String pack(byte[] iv, byte[] cipherBytes) {
        byte[] prefix = CryptoUtils.randomDigitBytes(4);
        byte[] suffix = CryptoUtils.randomDigitBytes(4);
        ByteBuffer buffer = ByteBuffer.allocate(prefix.length + 1 + iv.length + cipherBytes.length + 1 + suffix.length);
        buffer.put(prefix);
        buffer.put((byte) 0x00);
        buffer.put(iv);
        buffer.put(cipherBytes);
        buffer.put((byte) 0x00);
        buffer.put(suffix);
        return CryptoUtils.base64Encode(buffer.array());
    }

    private static void validate(byte[] decoded) {
        int minLen = CryptoConstants.PREFIX_LEN + CryptoConstants.IV_LEN + CryptoConstants.SUFFIX_LEN;
        if (decoded.length < minLen) {
            throw new IllegalArgumentException("密文长度不足，无法解析包裹");
        }
        byte prefixSep = decoded[CryptoConstants.PREFIX_LEN - 1];
        byte suffixSep = decoded[decoded.length - CryptoConstants.SUFFIX_LEN];
        if (prefixSep != 0x00 || suffixSep != 0x00) {
            throw new IllegalArgumentException("包裹分隔符不正确");
        }
        int ctLen = decoded.length - CryptoConstants.PREFIX_LEN - CryptoConstants.IV_LEN - CryptoConstants.SUFFIX_LEN;
        if (ctLen <= 0) {
            throw new IllegalArgumentException("密文区间为空");
        }
    }

    /**
     * 解析结果
     */
    @Getter
    public static class Parsed {
        private final byte[] iv;
        private final byte[] cipher;

        public Parsed(byte[] iv, byte[] cipher) {
            this.iv = iv;
            this.cipher = cipher;
        }
    }
}
