package com.ra.rabnbserver.server.miner;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.MinerAccelerationDTO;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.dto.adminMinerAction.AdminMinerActionDTO;
import com.ra.rabnbserver.dto.adminMinerAction.FragmentExchangeNftDTO;
import com.ra.rabnbserver.pojo.UserMiner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface MinerServe extends IService<UserMiner> {


    IPage<UserMiner> getUserMinerPage(Long userId, MinerQueryDTO query);

    void buyMinerBatch(Long userId, String minerType, int quantity, Integer cardId);

    @Transactional(rollbackFor = Exception.class)
    void payElectricity(Long userId, MinerElectricityDTO dto);

    @Transactional(rollbackFor = Exception.class)
    void buyAccelerationPack(Long userId, MinerAccelerationDTO dto);

    void processDailyProfit() throws Exception;

    /** 管理员代领取 */
    String adminClaimAll(AdminMinerActionDTO dto) throws Exception;

    /** 管理员代兑换未解锁碎片 */
    String adminExchangeLocked(AdminMinerActionDTO dto) throws Exception;

    /** 管理员代兑换已解锁碎片 */
    String adminExchangeUnlocked(AdminMinerActionDTO dto) throws Exception;

    /** 碎片换卡牌 */
    void buyNftWithFragments(Long userId, FragmentExchangeNftDTO dto) throws Exception;

    @Transactional(rollbackFor = Exception.class)
    void processDailyElectricityReward();
}
