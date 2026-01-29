package com.ra.rabnbserver.controller;


import com.ra.rabnbserver.enums.BaseEnum;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共接口
 * 获取枚举
 */
@Slf4j
@RestController
@RequestMapping("/api/common")
public class CommonController {

    /**
     * 一次性返回所有需要的枚举（适合前端初始化缓存）
     */
    @GetMapping("/enums/all")
    public String getAllEnums() {
        Map<String, List<Map<String, String>>> result = new HashMap<>();
        result.put("billTypes", enumToMap(BillType.values()));
        result.put("fundTypes", enumToMap(FundType.values()));
        result.put("transactionTypes", enumToMap(TransactionType.values()));
        return ApiResponse.success(result);
    }

    /**
     * 根据类型名称动态获取（适合按需加载）
     */
    @GetMapping("/enum")
    public String getEnumByType(@RequestParam String type) {
        // 这里可以维护一个 Map 或者通过反射获取
        // 简单直接的方式是使用 Switch 或 Map 映射
        return switch (type) {
            case "BillType" -> ApiResponse.success(enumToMap(BillType.values()));
            case "FundType" -> ApiResponse.success(enumToMap(FundType.values()));
            case "TransactionType" -> ApiResponse.success(enumToMap(TransactionType.values()));
            default -> ApiResponse.error("未找到指定的枚举类型");
        };
    }

    // 提取公共转换方法
    // 修改后的通用转换方法
    private <T extends BaseEnum> List<Map<String, String>> enumToMap(T[] values) {
        return Arrays.stream(values)
                .map(e -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("value", e.getCode());
                    map.put("description", e.getDesc());
                    return map;
                })
                .toList();
    }
}