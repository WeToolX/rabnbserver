package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.VO.AdminAssignSpecialMinerVO;
import com.ra.rabnbserver.dto.AssignSpecialMinerDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 用户矿机管理
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/miner")
public class AdminMinerController {
    private final MinerServe minerServe;

    public AdminMinerController(MinerServe minerServe) {
        this.minerServe = minerServe;
    }

    /**
     * 分页查询用户矿机列表
     */
    @SaCheckLogin
    @PostMapping("/list")
    public String getMinerList(@RequestBody(required = false) MinerQueryDTO query) {
        if (query == null) {
            query = new MinerQueryDTO();
        }
        log.info("getMinerList, query={}", query);
        try {
            IPage<UserMiner> result = minerServe.getUserMinerPage(0L, query);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * super_admin 为用户发放特殊矿机，仅发放不激活
     */
    @SaCheckRole("super_admin")
    @PostMapping("/assign-special")
    public String assignSpecialMiner(@RequestBody AssignSpecialMinerDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            return ApiResponse.error("用户ID不能为空");
        }
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return ApiResponse.error("发放数量必须大于0");
        }
        try {
            log.info("assignSpecialMiner, userId={}, quantity={}, remark={}",
                    dto.getUserId(), dto.getQuantity(), dto.getRemark());
            return ApiResponse.success("发放成功", new AdminAssignSpecialMinerVO(
                    dto.getUserId(),
                    dto.getQuantity(),
                    minerServe.assignSpecialMinerByAdmin(dto.getUserId(), dto.getQuantity(), dto.getRemark())
            ));
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("assignSpecialMiner error", e);
            return ApiResponse.error("发放失败: " + e.getMessage());
        }
    }

    /**
     * 启动矿机收益
     */
    @GetMapping("/ss/dd/ff")
    public String startMiner() throws Exception {
        log.info("startMiner");
        minerServe.processDailyProfit();
        return ApiResponse.success();
    }
}
