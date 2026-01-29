package com.ra.rabnbserver;

import com.ra.rabnbserver.contract.support.PrivateKeyCryptoService;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 私钥加密测试工具
 */
public class PrivateKeyEncryptTool {

    /**
     * 控制台入口
     *
     * @param args 启动参数（未使用）
     * @throws Exception 异常
     */
    public static void main(String[] args) throws Exception {
        System.out.print("请输入私钥明文: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String plainKey = reader.readLine();
        if (plainKey == null || plainKey.trim().isEmpty()) {
            System.out.println("私钥不能为空");
            return;
        }
        PrivateKeyCryptoService cryptoService = new PrivateKeyCryptoService();
        String encrypted = cryptoService.encryptPrivateKeyForConfig(plainKey.trim());
        System.out.println("加密结果: " + encrypted);
    }
}
