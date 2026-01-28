package com.ra.rabnbserver.crypto;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 请求解密与验签服务
 */
@Slf4j(topic = "com.ra.rabnbserver.service.crypto")
@Service
public class RequestCryptoService {

    /**
     * 解密请求体并校验签名
     *
     * @param cipherBase64 加密主体
     * @param signBase64   签名密文
     * @param userAgent    UA
     * @param token        账号 token
     * @param tokenMode    是否使用 token 作为解密 key
     */
    public DecryptResult decryptRequest(
            String cipherBase64,
            String signBase64,
            String userAgent,
            String token,
            boolean tokenMode
    ) {
        try {
            String ts7 = currentTs7();
            CryptoEnvelope.Parsed body = CryptoEnvelope.parse(cipherBase64);
            byte[] key = deriveKey(userAgent, token, tokenMode, ts7);

            // 先解签名
            CryptoEnvelope.Parsed sign = CryptoEnvelope.parse(signBase64);
            byte[] signPlainBytes = CryptoUtils.aesCbcDecryptPkcs5(key, sign.getIv(), sign.getCipher());
            signPlainBytes = CryptoUtils.stripPkcs7IfPresent(signPlainBytes);
            String signPlain = new String(signPlainBytes, StandardCharsets.UTF_8);

            String ivB64 = CryptoUtils.base64Encode(body.getIv());
            String cipherB64 = CryptoUtils.base64Encode(body.getCipher());
            String uaSafe = (userAgent == null ? "" : userAgent);
            String signSource = uaSafe + ts7 + cipherB64 + ivB64;
            String expected = CryptoUtils.md5Hex(signSource).toLowerCase();

            if (!expected.equals(signPlain)) {
                log.warn("验签失败，期望={}, 实际={}", expected, signPlain);
                return DecryptResult.fail("验签失败");
            }

            // 解密主体
            byte[] plainBytes = CryptoUtils.aesCbcDecryptPkcs5(key, body.getIv(), body.getCipher());
            plainBytes = CryptoUtils.stripPkcs7IfPresent(plainBytes);
            String plainText = new String(plainBytes, StandardCharsets.UTF_8);

            return DecryptResult.success(plainText);
        } catch (Exception e) {
            log.warn("解密失败: {}", e.getMessage());
            return DecryptResult.fail("解密失败");
        }
    }

    private byte[] deriveKey(String userAgent, String token, boolean tokenMode, String ts7) {
        if (tokenMode) {
            String keyStr = CryptoUtils.md5Hex(token + CryptoConstants.TOKEN_SALT);
            return CryptoUtils.md5Bytes(keyStr);
        }
        String uaSafe = (userAgent == null ? "" : userAgent);
        return CryptoUtils.md5Bytes(uaSafe + ts7);
    }

    private String currentTs7() {
        String now = String.valueOf(System.currentTimeMillis());
        // 保持与旧协议一致：取时间戳前 6 位作为 ts7
        return now.substring(0, Math.min(6, now.length()));
    }

    /**
     * 解密结果
     */
    @Getter
    public static class DecryptResult {
        private final boolean success;
        private final String message;
        private final String plainText;

        private DecryptResult(boolean success, String message, String plainText) {
            this.success = success;
            this.message = message;
            this.plainText = plainText;
        }

        public static DecryptResult success(String plainText) {
            return new DecryptResult(true, "成功", plainText);
        }

        public static DecryptResult fail(String message) {
            return new DecryptResult(false, message, null);
        }
    }
}
