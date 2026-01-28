package com.ra.rabnbserver.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum OrderType {
    PROCESSING(0, "处理中"),
    PLATFORM_RECHARGE(1, "平台充值"),
    BUY_STATIC(2, "购买静态理财"),
    BUY_DYNAMIC(3, "购买动态理财"),
    INTERNAL_BUY_STYAI(4, "内部交易购买STYAI"),
    INTERNAL_SELL_STYAI(5, "内部交易出售STYAI"),
    FLASH_SWAP(6, "闪兑换");

    private final int code;
    private final String label;

    OrderType(int code, String label) {
        this.code = code;
        this.label = label;
    }
    public int getCode() { return code; }
    public String getLabel() { return label; }

    private static final Map<Integer, OrderType> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(OrderType::getCode, e -> e));

    /** 通过 code 获取枚举，未知返回 null */
    public static OrderType of(Integer code) {
        return code == null ? null : BY_CODE.get(code);
    }

    /** 直接拿到中文标签；未知返回 "未知类型(xxx)" */
    public static String labelOf(Integer code) {
        OrderType t = of(code);
        return t == null ? ("未知类型(" + code + ")") : t.getLabel();
    }
}
