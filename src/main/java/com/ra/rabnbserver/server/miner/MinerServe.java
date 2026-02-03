package com.ra.rabnbserver.server.miner;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.pojo.UserMiner;
import org.springframework.transaction.annotation.Transactional;

public interface MinerServe extends IService<UserMiner> {
    void buyMiner(Long userId);

    @Transactional(rollbackFor = Exception.class)
    void activateMiner(Long userMinerId);

    void processDailyProfit();
}
