package com.ra.rabnbserver.server.miner;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.MinerAccelerationDTO;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.pojo.UserMiner;
import org.springframework.transaction.annotation.Transactional;

public interface MinerServe extends IService<UserMiner> {


    IPage<UserMiner> getUserMinerPage(Long userId, MinerQueryDTO query);

    @Transactional(rollbackFor = Exception.class)
    void buyMinerBatch(Long userId, String minerType, int quantity);

    @Transactional(rollbackFor = Exception.class)
    void payElectricity(Long userId, MinerElectricityDTO dto);

    @Transactional(rollbackFor = Exception.class)
    void buyAccelerationPack(Long userId, MinerAccelerationDTO dto);

    void processDailyProfit();
}
