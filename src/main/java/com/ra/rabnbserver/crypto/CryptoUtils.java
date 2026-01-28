package com.ra.rabnbserver.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加解密工具
 */
public final class CryptoUtils {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String SEED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_@#";
    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private CryptoUtils() {
    }

    public static byte[] md5Bytes(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    public static String md5Hex(String input) {
        byte[] digest = md5Bytes(input);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    public static byte[] pkcs7Pad(byte[] src) {
        int block = 16;
        int mod = src.length % block;
        int padLen = (mod == 0) ? block : (block - mod);
        byte[] out = new byte[src.length + padLen];
        System.arraycopy(src, 0, out, 0, src.length);
        for (int i = src.length; i < out.length; i++) {
            out[i] = (byte) padLen;
        }
        return out;
    }

    public static byte[] aesCbcEncryptNoPadding(byte[] key16, byte[] iv16, byte[] paddedPlain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key16, "AES"), new IvParameterSpec(iv16));
            return cipher.doFinal(paddedPlain);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public static byte[] aesCbcDecryptPkcs5(byte[] key16, byte[] iv16, byte[] cipherBytes) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key16, "AES"), new IvParameterSpec(iv16));
            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    public static byte[] stripPkcs7IfPresent(byte[] src) {
        if (src == null || src.length == 0) {
            return src;
        }
        int pad = src[src.length - 1] & 0xFF;
        if (pad < 1 || pad > 16) {
            return src;
        }
        int n = src.length;
        for (int i = 1; i <= pad; i++) {
            if ((src[n - i] & 0xFF) != pad) {
                return src;
            }
        }
        byte[] out = new byte[n - pad];
        System.arraycopy(src, 0, out, 0, out.length);
        return out;
    }

    public static String randomSeed() {
        int len = 16 + RNG.nextInt(9);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(SEED_CHARS.charAt(RNG.nextInt(SEED_CHARS.length())));
        }
        return sb.toString();
    }

    public static String randomText10to50() {
        int len = 10 + RNG.nextInt(41);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALNUM.charAt(RNG.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }

    public static byte[] randomDigitBytes(int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) (1 + RNG.nextInt(9));
        }
        return out;
    }
}
