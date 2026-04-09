package com.ra.rabnbserver.server.card;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.EtfCardQueryDTO;
import com.ra.rabnbserver.pojo.ETFCard;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface EtfCardServe extends IService<ETFCard> {
    IPage<ETFCard> getBatchPage(EtfCardQueryDTO query);

    @Transactional(rollbackFor = Exception.class)
    void addBatch(ETFCard card);

    @Transactional(rollbackFor = Exception.class)
    void updateBatch(ETFCard card);

    @Transactional(rollbackFor = Exception.class)
    void deleteBatch(Long id);

    @Transactional(rollbackFor = Exception.class)
    void activeBatch(Long id);

    ETFCard getActiveAndEnabledBatch(Integer cardId);

    Map<Integer, ETFCard> getActiveAndEnabledBatchMap(List<Integer> cardIds);
}
