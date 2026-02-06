package com.ra.rabnbserver.controller.abnormal;

import com.ra.rabnbserver.VO.AbnormalPageVO;
import com.ra.rabnbserver.dto.AbnormalQueryDTO;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 -- 异常处理列表
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/abnormal")
public class AdminAbnormalController {

    private final AbnormalRetryManager abnormalRetryManager;

    /**
     * 分页查询异常处理数据
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @PostMapping("/list")
    public String list(@RequestBody(required = false) AbnormalQueryDTO query) {
        try {
            AbnormalPageVO result = abnormalRetryManager.queryAbnormalPage(query);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            log.error("查询异常处理列表失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}
