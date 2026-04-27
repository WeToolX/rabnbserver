package com.ra.rabnbserver.controller.card.admin;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionRecordVO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantCommissionQueryDTO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantAccountQueryDTO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantWindowQueryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.GoldQuantAccount;
import com.ra.rabnbserver.pojo.GoldQuantWindow;
import com.ra.rabnbserver.server.gold.GoldQuantCommissionService;
import com.ra.rabnbserver.server.gold.GoldQuantServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/card/gold-quant")
@RequiredArgsConstructor
public class AdminGoldQuantController {
    private final GoldQuantServe goldQuantServe;
    private final GoldQuantCommissionService goldQuantCommissionService;

    @SaCheckLogin
    @PostMapping("/account/list")
    public String accountList(@RequestBody(required = false) AdminGoldQuantAccountQueryDTO query) {
        try {
            IPage<GoldQuantAccount> result = goldQuantServe.getAdminAccountPage(query);
            return ApiResponse.success("获取成功", result);
        } catch (java.time.format.DateTimeParseException | cn.hutool.core.date.DateException e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
        } catch (Exception e) {
            log.error("查询黄金量化托管费列表失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/window/list")
    public String windowList(@RequestBody(required = false) AdminGoldQuantWindowQueryDTO query) {
        try {
            IPage<GoldQuantWindow> result = goldQuantServe.getAdminWindowPage(query);
            return ApiResponse.success("获取成功", result);
        } catch (java.time.format.DateTimeParseException | cn.hutool.core.date.DateException e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
        } catch (Exception e) {
            log.error("查询黄金量化窗口列表失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/commission/list")
    public String commissionList(@RequestBody(required = false) AdminGoldQuantCommissionQueryDTO query) {
        try {
            IPage<GoldQuantCommissionRecordVO> result = goldQuantCommissionService.getAdminCommissionPage(query);
            return ApiResponse.success("获取成功", result);
        } catch (java.time.format.DateTimeParseException | cn.hutool.core.date.DateException e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
        } catch (Exception e) {
            log.error("查询黄金量化分成记录失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/commission/statistics")
    public String commissionStatistics(@RequestBody(required = false) AdminGoldQuantCommissionQueryDTO query) {
        try {
            return ApiResponse.success("获取成功", goldQuantCommissionService.getAdminCommissionStatistics(query));
        } catch (java.time.format.DateTimeParseException | cn.hutool.core.date.DateException e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
        } catch (Exception e) {
            log.error("统计黄金量化分成记录失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}
