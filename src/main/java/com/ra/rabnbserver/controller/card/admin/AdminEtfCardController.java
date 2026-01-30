package com.ra.rabnbserver.controller.card.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.controller.card.user.EtfCardController;
import com.ra.rabnbserver.dto.EtfCardQueryDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.server.card.EtfCardServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


/**
 * 管理员 -- ETF 卡牌批次管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/card")
public class AdminEtfCardController{
    private final EtfCardServe etfCardServe;


    public AdminEtfCardController(EtfCardServe etfCardServe) {
        this.etfCardServe = etfCardServe;
    }

    /**
     * 获取批次列表（分页+筛选）
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
        if (card.getId() == null) return ApiResponse.error("ID不能为空");
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
     * 激活批次（设为当前唯一激活）
     */
    @PostMapping("/batch/active/{id}")
    public String active(@PathVariable Long id) {
        etfCardServe.activeBatch(id);
        return ApiResponse.success("激活成功，该批次现已生效");
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
