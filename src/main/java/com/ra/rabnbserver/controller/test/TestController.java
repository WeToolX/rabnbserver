package com.ra.rabnbserver.controller.test;

import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.server.test.TestServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异常处理框架测试接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/test")
public class TestController {

    private final TestServe testServe;

    /**
     * 测试支付失败数据写入（用于异常处理框架）
     *
     * @param userValue 用户标识（默认 1000）
     * @return 写入的主键
     */
    @PostMapping("/payment/fail")
    public String createFailPayment(@RequestParam(value = "userValue", required = false) String userValue) {
        String finalUser = (userValue == null || userValue.isBlank()) ? "1000" : userValue;
        testServe.entryExample(finalUser);
        Long dataId = testServe.createFailPayment(finalUser);
        log.info("测试支付失败数据写入成功，dataId={}, user={}", dataId, finalUser);
        return ApiResponse.success("写入失败数据成功", dataId);
    }

    /**
     * 人工处理成功回调（示例）
     *
     * @param dataId 数据主键
     * @return 处理结果
     */
    @PostMapping("/payment/manual-success")
    public String manualSuccess(@RequestParam("dataId") Long dataId) {
        testServe.manualSuccessExample(dataId);
        log.info("人工处理成功已回调，dataId={}", dataId);
        return ApiResponse.success("人工处理成功", dataId);
    }
}
