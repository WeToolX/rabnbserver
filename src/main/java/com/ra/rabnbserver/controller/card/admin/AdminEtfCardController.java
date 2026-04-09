package com.ra.rabnbserver.controller.card.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.VO.ActiveEtfCardBatchVO;
import com.ra.rabnbserver.dto.EtfCardQueryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.server.card.EtfCardServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 -- ETF 卡牌批次管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/card")
public class AdminEtfCardController {

    private static final List<Integer> ACTIVE_CARD_IDS = List.of(1, 2, 3);

    private final EtfCardServe etfCardServe;

    public AdminEtfCardController(EtfCardServe etfCardServe) {
        this.etfCardServe = etfCardServe;
    }

    /**
     * 获取批次列表（分页 + 筛选）
     */
    @PostMapping("/batch/list")
    public String list(@RequestBody EtfCardQueryDTO query) {
        IPage<ETFCard> result = etfCardServe.getBatchPage(query);
        return ApiResponse.success("获取成功", result);
    }

    /**
     * 新增发行批次
     */
    @PostMapping("/batch/add")
    public String add(@RequestBody ETFCard card) {
        etfCardServe.addBatch(card);
        return ApiResponse.success("添加成功");
    }

    /**
     * 修改发行批次
     */
    @PostMapping("/batch/update")
    public String update(@RequestBody ETFCard card) {
        if (card.getId() == null) {
            return ApiResponse.error("ID不能为空");
        }
        etfCardServe.updateBatch(card);
        return ApiResponse.success("修改成功");
    }

    /**
     * 删除发行批次
     */
    @GetMapping("/batch/delete/{id}")
    public String delete(@PathVariable Long id) {
        etfCardServe.deleteBatch(id);
        return ApiResponse.success("删除成功");
    }

    /**
     * 激活指定卡牌类型下的当前批次
     */
    @PostMapping("/batch/active/{id}")
    public String active(@PathVariable Long id) {
        etfCardServe.activeBatch(id);
        return ApiResponse.success("激活成功，该类型批次已生效");
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
