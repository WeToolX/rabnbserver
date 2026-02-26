package com.ra.rabnbserver.controller.bill;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.VO.AdminBillStatisticsVO;
import com.ra.rabnbserver.dto.admin.bill.AdminBillQueryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.user.UserBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 -- 账单管理
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/bill")
public class AdminBillController {

    private final UserBillServe userBillService;

    /**
     * 分页查询账单列表
     * 支持：时间范围、钱包地址、交易类型、资金类型、账单类型、状态
     */
    @GetMapping("/list")
    public String list(AdminBillQueryDTO query) {
        log.info("管理员查询账单列表: {}", query);
        try {
            IPage<UserBill> result = userBillService.getAdminBillPage(query);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询账单异常", e);
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取账单平台资金统计数据
     */
    @GetMapping("/statistics")
    public String getStatistics() {
        log.info("管理员获取平台资金统计");
        try {
            AdminBillStatisticsVO statistics = userBillService.getPlatformStatistics();
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("统计平台资金异常", e);
            return ApiResponse.error("统计失败：" + e.getMessage());
        }
    }
}