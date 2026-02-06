package com.ra.rabnbserver.server.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.dto.EtfCardQueryDTO;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.EtfCardMapper;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.server.card.EtfCardServe;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
public class EtfCardServeImpl extends ServiceImpl<EtfCardMapper, ETFCard> implements EtfCardServe {

    @Override
    public IPage<ETFCard> getBatchPage(EtfCardQueryDTO query) {
        LambdaQueryWrapper<ETFCard> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getBatchName())) {
            wrapper.like(ETFCard::getBatchName, query.getBatchName());
        }
        if (StringUtils.isNotBlank(query.getBatchNo())) {
            wrapper.eq(ETFCard::getBatchNo, query.getBatchNo());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ETFCard::getStatus, query.getStatus());
        }
        if (query.getIsCurrent() != null) {
            wrapper.eq(ETFCard::getIsCurrent, query.getIsCurrent());
        }
        wrapper.orderByDesc(ETFCard::getId);
        return this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addBatch(ETFCard card) {
        // 发行总量校验：必须大于0
        if (card.getTotalSupply() == null || card.getTotalSupply() <= 0) {
            throw new BusinessException("新增失败：发行总量(totalSupply)必须填写且大于0");
        }
        if (StringUtils.isBlank(card.getBatchNo()) || "0".equals(card.getBatchNo())) {
            String autoBatchNo = "BNO" + System.currentTimeMillis();
            card.setBatchNo(autoBatchNo);
            log.info("未检测到卡牌批次编号，已自动生成: {}", autoBatchNo);
        }
        card.setIsCurrent(0);                     // 默认不激活
        card.setSoldCount(0);                     // 初始已售为0
        card.setInventory(card.getTotalSupply()); // 强制：库存 = 发行总量
        if (card.getStatus() == null) {
            card.setStatus(1); // 默认启用
        }
        if (card.getUnitPrice() == null) {
            card.setUnitPrice(BigDecimal.ZERO); // 默认单价0
        }
        if (StringUtils.isBlank(card.getBatchName())) {
            card.setBatchName("未命名卡牌_" + card.getBatchNo());
        }
        // 执行保存
        boolean saved = this.save(card);
        if (!saved) {
            throw new BusinessException("数据库写入失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateBatch(ETFCard card) {
        // 基本校验
        if (card.getId() == null) {
            throw new BusinessException("更新失败：ID不能为空");
        }
        // 检查旧数据状态
        ETFCard existing = this.getById(card.getId());
        if (existing == null) {
            throw new BusinessException("卡牌批次不存在");
        }
        // 校验：不能修改当前激活批次
//        if (existing.getIsCurrent() == 1) {
//            throw new BusinessException("当前激活中的卡牌批次禁止修改，请先切换激活卡牌批次或停用");
//        }
        // 执行受限更新：只允许修改 名称、状态、单价、备注
        // 使用 LambdaUpdateWrapper 确保即便前端传了其他字段（如库存），也不会被写入数据库
        boolean success = this.update(new LambdaUpdateWrapper<ETFCard>()
                .eq(ETFCard::getId, card.getId())
                .set(card.getBatchName() != null, ETFCard::getBatchName, card.getBatchName())
                .set(card.getStatus() != null, ETFCard::getStatus, card.getStatus())
                .set(card.getUnitPrice() != null, ETFCard::getUnitPrice, card.getUnitPrice())
                .set(card.getRemark() != null, ETFCard::getRemark, card.getRemark())
                .set(card.getInventory() != null, ETFCard::getInventory, card.getInventory())
                .set(card.getTotalSupply() != null, ETFCard::getTotalSupply, card.getTotalSupply())
        );
        if (!success) {
            throw new BusinessException("更新失败");
        }
        log.info("卡牌批次信息已修改: ID={}, 允许修改的字段已同步", card.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteBatch(Long id) {
        ETFCard existing = this.getById(id);
        if (existing == null) return;
        // 校验：不能删除当前激活批次
        if (existing.getIsCurrent() == 1) {
            throw new BusinessException("不能删除当前正在激活使用的ETF卡牌批次");
        }
        this.removeById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void activeBatch(Long id) {
        ETFCard target = this.getById(id);
        if (target == null) throw new BusinessException("目标ETF卡牌批次不存在");
        if (target.getStatus() == 0) throw new BusinessException("该ETF卡牌批次已停用，无法激活");
        // 将所有批次的激活状态置为 0
        this.update(new LambdaUpdateWrapper<ETFCard>()
                .set(ETFCard::getIsCurrent, 0));
        // 将指定 ID 的批次激活状态置为 1
        this.update(new LambdaUpdateWrapper<ETFCard>()
                .eq(ETFCard::getId, id)
                .set(ETFCard::getIsCurrent, 1));
        log.info("已切换当前激活卡牌批次为: ID={}, 名称={}", id, target.getBatchName());
    }

    @Override
    public ETFCard getActiveAndEnabledBatch() {
        // 查询条件：is_current = 1 并且 status = 1
        ETFCard batch = this.getOne(new LambdaQueryWrapper<ETFCard>()
                .eq(ETFCard::getIsCurrent, 1)
                .eq(ETFCard::getStatus, 1)
                .last("LIMIT 1")); // 兜底逻辑，确保只取一条
        if (batch == null) {
            log.warn("系统中当前没有激活且启用的 ETF卡牌 批次");
        }
        return batch;
    }
}