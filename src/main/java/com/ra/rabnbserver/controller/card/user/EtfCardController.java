package com.ra.rabnbserver.controller.card.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.dto.EtfCardQueryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.server.card.EtfCardServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端 - ETF 卡牌批次管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user/card")
public class EtfCardController {

    private final EtfCardServe etfCardServe;

    public EtfCardController(EtfCardServe etfCardServe) {
        this.etfCardServe = etfCardServe;
    }

    /**
     * 获取当前正在激活且启用的批次信息
     */
    @GetMapping("/batch/active-info")
    public String getActiveInfo() {
        ETFCard batch = etfCardServe.getActiveAndEnabledBatch();
        if (batch == null) {
            return ApiResponse.error("当前没有可售卖的ETF卡牌");
        }
        return ApiResponse.success("获取成功", batch);
    }
}