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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EtfCardServeImpl extends ServiceImpl<EtfCardMapper, ETFCard> implements EtfCardServe {

    private static final List<Integer> SUPPORTED_CARD_IDS = List.of(1, 2, 3);

    @Override
    public IPage<ETFCard> getBatchPage(EtfCardQueryDTO query) {
        LambdaQueryWrapper<ETFCard> wrapper = new LambdaQueryWrapper<>();
        if (query.getCardId() != null) {
            wrapper.eq(ETFCard::getCardId, query.getCardId());
        }
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
        validateCardId(card.getCardId());
        if (card.getTotalSupply() == null || card.getTotalSupply() <= 0) {
            throw new BusinessException("新增失败：发行总量(totalSupply)必须填写且大于0");
        }
        if (StringUtils.isBlank(card.getBatchNo()) || "0".equals(card.getBatchNo())) {
            String autoBatchNo = "BNO" + System.currentTimeMillis();
            card.setBatchNo(autoBatchNo);
            log.info("未检测到卡牌批次编号，已自动生成: {}", autoBatchNo);
        }
        card.setIsCurrent(0);
        card.setSoldCount(0);
        card.setInventory(card.getTotalSupply());
        if (card.getStatus() == null) {
            card.setStatus(1);
        }
        if (card.getUnitPrice() == null) {
            card.setUnitPrice(BigDecimal.ZERO);
        }
        if (StringUtils.isBlank(card.getBatchName())) {
            card.setBatchName("未命名卡牌_" + card.getBatchNo());
        }
        boolean saved = this.save(card);
        if (!saved) {
            throw new BusinessException("数据库写入失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateBatch(ETFCard card) {
        if (card.getId() == null) {
            throw new BusinessException("更新失败：ID不能为空");
        }
        ETFCard existing = this.getById(card.getId());
        if (existing == null) {
            throw new BusinessException("卡牌批次不存在");
        }
        if (card.getCardId() != null && !card.getCardId().equals(existing.getCardId())) {
            throw new BusinessException("不允许修改卡牌类型");
        }
        boolean success = this.update(new LambdaUpdateWrapper<ETFCard>()
                .eq(ETFCard::getId, card.getId())
                .set(card.getBatchName() != null, ETFCard::getBatchName, card.getBatchName())
                .set(card.getStatus() != null, ETFCard::getStatus, card.getStatus())
                .set(card.getUnitPrice() != null, ETFCard::getUnitPrice, card.getUnitPrice())
                .set(card.getRemark() != null, ETFCard::getRemark, card.getRemark())
                .set(card.getInventory() != null, ETFCard::getInventory, card.getInventory())
                .set(card.getTotalSupply() != null, ETFCard::getTotalSupply, card.getTotalSupply()));
        if (!success) {
            throw new BusinessException("更新失败");
        }
        log.info("卡牌批次信息已修改: id={}, cardId={}", card.getId(), existing.getCardId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteBatch(Long id) {
        ETFCard existing = this.getById(id);
        if (existing == null) {
            return;
        }
        if (existing.getIsCurrent() == 1) {
            throw new BusinessException("不能删除当前正在激活使用的ETF卡牌批次");
        }
        this.removeById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void activeBatch(Long id) {
        ETFCard target = this.getById(id);
        if (target == null) {
            throw new BusinessException("目标ETF卡牌批次不存在");
        }
        validateCardId(target.getCardId());
        if (target.getStatus() == 0) {
            throw new BusinessException("该ETF卡牌批次已停用，无法激活");
        }
        this.update(new LambdaUpdateWrapper<ETFCard>()
                .eq(ETFCard::getCardId, target.getCardId())
                .set(ETFCard::getIsCurrent, 0));
        this.update(new LambdaUpdateWrapper<ETFCard>()
                .eq(ETFCard::getId, id)
                .set(ETFCard::getIsCurrent, 1));
        log.info("已切换当前激活卡牌批次: cardId={}, id={}, batchName={}",
                target.getCardId(), id, target.getBatchName());
    }

    @Override
    public ETFCard getActiveAndEnabledBatch(Integer cardId) {
        validateCardId(cardId);
        ETFCard batch = this.getOne(new LambdaQueryWrapper<ETFCard>()
                .eq(ETFCard::getCardId, cardId)
                .eq(ETFCard::getIsCurrent, 1)
                .eq(ETFCard::getStatus, 1)
                .last("LIMIT 1"));
        if (batch == null) {
            log.warn("当前没有可售卖的ETF卡牌批次: cardId={}", cardId);
        }
        return batch;
    }

    @Override
    public Map<Integer, ETFCard> getActiveAndEnabledBatchMap(List<Integer> cardIds) {
        LinkedHashMap<Integer, ETFCard> result = new LinkedHashMap<>();
        if (cardIds == null || cardIds.isEmpty()) {
            return result;
        }
        for (Integer cardId : cardIds) {
            validateCardId(cardId);
            result.put(cardId, null);
        }
        List<ETFCard> batches = this.list(new LambdaQueryWrapper<ETFCard>()
                .in(ETFCard::getCardId, result.keySet())
                .eq(ETFCard::getIsCurrent, 1)
                .eq(ETFCard::getStatus, 1)
                .orderByDesc(ETFCard::getId));
        for (ETFCard batch : batches) {
            if (result.get(batch.getCardId()) == null) {
                result.put(batch.getCardId(), batch);
            }
        }
        return result;
    }

    private void validateCardId(Integer cardId) {
        if (cardId == null || !SUPPORTED_CARD_IDS.contains(cardId)) {
            throw new BusinessException("卡牌ID必须为1、2、3");
        }
    }
}
