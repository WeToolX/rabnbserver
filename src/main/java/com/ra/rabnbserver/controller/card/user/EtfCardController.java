package com.ra.rabnbserver.controller.card.user;

import com.ra.rabnbserver.VO.ActiveEtfCardBatchVO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.server.card.EtfCardServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户端 - ETF 卡牌批次管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user/card")
public class EtfCardController {

    private static final List<Integer> ACTIVE_CARD_IDS = List.of(1, 2, 3);

    private final EtfCardServe etfCardServe;

    public EtfCardController(EtfCardServe etfCardServe) {
        this.etfCardServe = etfCardServe;
    }

    /**
     * 获取当前激活且启用的三种卡牌批次信息
     */
    @GetMapping("/batch/active-info")
    public String getActiveInfo() {
        Map<Integer, ETFCard> batchMap = etfCardServe.getActiveAndEnabledBatchMap(ACTIVE_CARD_IDS);
        ActiveEtfCardBatchVO vo = buildActiveBatchVO(batchMap);
        return ApiResponse.success("获取成功", vo);
    }

    private ActiveEtfCardBatchVO buildActiveBatchVO(Map<Integer, ETFCard> batchMap) {
        ActiveEtfCardBatchVO vo = new ActiveEtfCardBatchVO();
        vo.setCard1Batch(batchMap.get(1));
        vo.setCard2Batch(batchMap.get(2));
        vo.setCard3Batch(batchMap.get(3));
        return vo;
    }
}
