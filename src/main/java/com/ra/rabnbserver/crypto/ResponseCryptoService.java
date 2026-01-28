package com.ra.rabnbserver.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 响应加密服务
 */
@Service
public class ResponseCryptoService {

    /**
     * 对响应明文进行加密并输出自定义摩斯编码
     */
    public String encryptToMorse(String plainJson, String token) {
        if (plainJson == null) {
            return "";
        }
        String keyStr = CryptoUtils.md5Hex(token + CryptoConstants.TOKEN_SALT);
        String mixed = keyStr + token;
        String mdKeys = CryptoUtils.md5Hex(mixed);

        return encryptToMorseWithKey(plainJson, mdKeys);
    }

    private String encryptEnvelope(String plainJson, String keyText) {
        byte[] key = CryptoUtils.md5Bytes(keyText);
        byte[] iv = CryptoUtils.md5Bytes(CryptoUtils.randomSeed());
        byte[] padded = CryptoUtils.pkcs7Pad(plainJson.getBytes(StandardCharsets.UTF_8));
        byte[] cipher = CryptoUtils.aesCbcEncryptNoPadding(key, iv, padded);
        return CryptoEnvelope.pack(iv, cipher);
    }

    /**
     * 使用指定 key 进行加密并输出摩斯编码
     */
    public String encryptToMorseWithKey(String plainJson, String keyText) {
        if (plainJson == null) {
            return "";
        }
        String base64Envelope = encryptEnvelope(plainJson, keyText);
        return MorseCodec.encodeBase64ToCustomMorse(base64Envelope);
    }
}
