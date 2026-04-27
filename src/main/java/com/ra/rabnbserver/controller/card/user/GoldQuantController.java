package com.ra.rabnbserver.controller.card.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionRecordVO;
import com.ra.rabnbserver.dto.gold.GoldQuantQuantityDTO;
import com.ra.rabnbserver.dto.gold.GoldQuantCommissionQueryDTO;
import com.ra.rabnbserver.dto.gold.GoldQuantWindowRenewDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.server.gold.GoldQuantCommissionService;
import com.ra.rabnbserver.server.gold.GoldQuantServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/user/card/gold-quant")
@RequiredArgsConstructor
public class GoldQuantController {
    private final GoldQuantServe goldQuantServe;
    private final GoldQuantCommissionService goldQuantCommissionService;

    @Value("${ADMIN.ISOPEN:true}")
    private Boolean isOpen;

    @SaCheckLogin
    @GetMapping("/home")
    public String home() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            return ApiResponse.success("获取成功", goldQuantServe.getHome(getFormalUserId()));
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/pay-hosting")
    public String payHosting() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.payHosting(getFormalUserId());
            return ApiResponse.success("托管费缴纳成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("黄金量化托管费缴纳失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/buy-window")
    public String buyWindow(@RequestBody GoldQuantQuantityDTO dto) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.buyWindow(getFormalUserId(), dto == null ? null : dto.getQuantity());
            return ApiResponse.success("新购量化窗口成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("新购黄金量化窗口失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/renew-window")
    public String renewWindow(@RequestBody GoldQuantWindowRenewDTO dto) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.renewWindow(getFormalUserId(), dto == null ? null : dto.getWindowId());
            return ApiResponse.success("窗口续费成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("黄金量化窗口续费失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/batch-renew")
    public String batchRenew(@RequestBody GoldQuantQuantityDTO dto) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            goldQuantServe.batchRenew(getFormalUserId(), dto == null ? null : dto.getQuantity());
            return ApiResponse.success("维护批量续费成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("黄金量化批量续费失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @GetMapping("/team/summary")
    public String teamSummary() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            return ApiResponse.success("获取成功", goldQuantCommissionService.getTeamSummary(getFormalUserId()));
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询黄金量化团队汇总失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @GetMapping("/team/areas")
    public String teamAreas() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            return ApiResponse.success("获取成功", goldQuantCommissionService.getTeamAreas(getFormalUserId()));
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询黄金量化团队大小区失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/commission-records")
    public String commissionRecords(@RequestBody(required = false) GoldQuantCommissionQueryDTO query) {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            IPage<GoldQuantCommissionRecordVO> result = goldQuantCommissionService.getUserCommissionPage(getFormalUserId(), query);
            return ApiResponse.success("获取成功", result);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (java.time.format.DateTimeParseException | cn.hutool.core.date.DateException e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
        } catch (Exception e) {
            log.error("查询黄金量化分成记录失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    @SaCheckLogin
    @GetMapping("/commission-rules")
    public String commissionRules() {
        if (!isOpen) {
            return ApiResponse.error("暂未开放！");
        }
        try {
            return ApiResponse.success("获取成功", goldQuantCommissionService.getRules());
        } catch (Exception e) {
            log.error("查询黄金量化分成规则失败", e);
            return ApiResponse.error("系统处理失败");
        }
    }

    private Long getFormalUserId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (!StrUtil.isNumeric(loginId)) {
            throw new BusinessException("请先登录账号");
        }
        return Long.parseLong(loginId);
    }
}
