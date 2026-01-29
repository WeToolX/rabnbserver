package com.ra.rabnbserver.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用响应结构
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 状态码
     */
    private int code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    public static <T> String success(T data) {
        return toJson(new ApiResponse<>(200, "操作成功", data));
    }

    public static <T> String success(String message,T data) {
        return toJson(new ApiResponse<>(200, message, data));
    }

    public static String success() {
        return toJson(new ApiResponse<>(200, "操作成功", null));
    }

    public static String error(String message) {
        return toJson(new ApiResponse<>(401, message, null));
    }

    public static String error(int code, String message) {
        return toJson(new ApiResponse<>(code, message, null));
    }

    private static String toJson(ApiResponse<?> response) {
        try {
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("响应序列化失败:{}",e.getMessage());
            return "{\"code\":500,\"message\":\"响应序列化失败\",\"data\":null}";
        }
    }
}
