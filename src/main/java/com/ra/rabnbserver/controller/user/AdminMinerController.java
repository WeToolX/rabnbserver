package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员 - 用户矿机管理
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
        log.info("getMinerList,查询参数：{}", query.toString());
        if (query == null) query = new MinerQueryDTO();
        try {
            IPage<UserMiner> result = minerServe.getUserMinerPage(0L, query);
            log.info("getMinerList result={}", result);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
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
