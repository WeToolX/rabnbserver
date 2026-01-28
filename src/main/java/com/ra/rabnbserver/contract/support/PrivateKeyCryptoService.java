package com.ra.rabnbserver.contract.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 合约私钥加解密服务（AES/CBC + Base64）
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class PrivateKeyCryptoService {

    /**
     * 加密密钥（随机生成写入代码，生产需妥善保管）
     */
    private static final String CIPHER_KEY = "SYkVCVzIMNKGLGpSMFQvQYoK+bBoaaloK0/x/I05tQI=";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 解密私钥
     *
     * @param encryptedBase64 密文（格式：ivBase64:cipherBase64）
     * @return 私钥明文（去除 0x 前缀）
     */
    public String decryptPrivateKey(String encryptedBase64) {
        try {
            String[] parts = encryptedBase64.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("私钥密文格式错误，应为 ivBase64:cipherBase64");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherBytes = Base64.getDecoder().decode(parts[1]);
            byte[] key = buildAesKey(CIPHER_KEY);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            String plain = new String(plainBytes, StandardCharsets.UTF_8);
            return strip0x(plain.trim());
        } catch (Exception e) {
            log.error("私钥解密失败: {}", e.getMessage());
            throw new IllegalStateException("私钥解密失败", e);
        }
    }

    /**
     * 加密私钥（测试使用，生成配置密文）
     *
     * @param plainPrivateKey 私钥明文
     * @return 密文（格式：ivBase64:cipherBase64）
     */
    public String encryptPrivateKeyForConfig(String plainPrivateKey) {
        try {
            byte[] iv = new byte[16];
            SECURE_RANDOM.nextBytes(iv);
            byte[] key = buildAesKey(CIPHER_KEY);
            String plain = strip0x(plainPrivateKey);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String cipherBase64 = Base64.getEncoder().encodeToString(cipherBytes);
            return ivBase64 + ":" + cipherBase64;
        } catch (Exception e) {
            log.error("私钥加密失败: {}", e.getMessage());
            throw new IllegalStateException("私钥加密失败", e);
        }
    }

    /**
     * 生成 AES Key（32 字节）
     *
     * @param seed 随机密钥文本
     * @return AES 密钥字节数组
     */
    private byte[] buildAesKey(String seed) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(seed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 去除私钥 0x 前缀
     *
     * @param key 私钥
     * @return 去前缀后的私钥
     */
    private String strip0x(String key) {
        if (key == null) {
            return "";
        }
        if (key.startsWith("0x") || key.startsWith("0X")) {
            return key.substring(2);
        }
        return key;
    }
}
