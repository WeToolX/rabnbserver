package com.ra.rabnbserver.enums;

public enum EncryptType {
    /**
     * 深度加密 (一表一密钥，一行一IV)
     */
    DEEP,
    /**
     * 浅层加密 (固定密钥和IV)
     */
    SHALLOW
}
