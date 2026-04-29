package com.ra.rabnbserver.server.miner;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.VO.GetAdminClaimVO;
import com.ra.rabnbserver.VO.miner.AdminMinerUserStatisticsVO;
import com.ra.rabnbserver.dto.MinerElectricityActionDTO;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.dto.adminMinerAction.AdminMinerActionDTO;
import com.ra.rabnbserver.dto.adminMinerAction.FragmentExchangeNftDTO;
import com.ra.rabnbserver.dto.miner.AdminMinerUserStatisticsQueryDTO;
import com.ra.rabnbserver.dto.miner.FragmentTransferDTO;
import com.ra.rabnbserver.pojo.UserMiner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MinerServe extends IService<UserMiner> {

    IPage<UserMiner> getUserMinerPage(Long userId, MinerQueryDTO query);

    IPage<AdminMinerUserStatisticsVO> getAdminUserStatisticsPage(AdminMinerUserStatisticsQueryDTO query);

    @Transactional(rollbackFor = Exception.class)
    List<Long> assignSpecialMinerByAdmin(Long userId, Integer quantity, String remark);

    void buyMinerBatch(Long userId, String minerType, int quantity, Integer cardId);

    @Transactional(rollbackFor = Exception.class)
    void payElectricity(Long userId, MinerElectricityDTO dto);

    @Transactional(rollbackFor = Exception.class)
    void activateElectricity(Long userId, MinerElectricityActionDTO dto);

    @Transactional(rollbackFor = Exception.class)
    void renewElectricity(Long userId, MinerElectricityActionDTO dto);

    void processDailyProfit() throws Exception;

    String adminClaimAll(GetAdminClaimVO dto) throws Exception;

    String adminExchangeLocked(AdminMinerActionDTO dto) throws Exception;

    String adminExchangeUnlocked(AdminMinerActionDTO dto) throws Exception;

    void buyNftWithFragments(Long userId, FragmentExchangeNftDTO dto) throws Exception;

    @Transactional(rollbackFor = Exception.class)
    void transferFragment(Long userId, FragmentTransferDTO dto);

    void recalculateUserGrade(Long userId);

    void recalculateAllUserGrades();

    void recalculateUserGradeForTeamArea(Long userId);

    @Transactional(rollbackFor = Exception.class)
    void processDailyElectricityReward();
}
