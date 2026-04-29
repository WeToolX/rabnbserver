package com.ra.rabnbserver.server.gold;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.VO.gold.AdminGoldQuantUserStatisticsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionRecordVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionSettingsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantCommissionStatisticsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantTeamAreaVO;
import com.ra.rabnbserver.VO.gold.GoldQuantTeamSummaryVO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantCommissionQueryDTO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantUserStatisticsQueryDTO;
import com.ra.rabnbserver.dto.gold.GoldQuantCommissionQueryDTO;
import com.ra.rabnbserver.pojo.GoldQuantCommissionRecord;

import java.math.BigDecimal;
import java.util.List;

public interface GoldQuantCommissionService extends IService<GoldQuantCommissionRecord> {
    void settleWindowOrder(Long sourceUserId, String sourceOrderId, BigDecimal orderAmount);

    GoldQuantTeamSummaryVO getTeamSummary(Long userId);

    List<GoldQuantTeamAreaVO> getTeamAreas(Long userId);

    IPage<GoldQuantCommissionRecordVO> getUserCommissionPage(Long userId, GoldQuantCommissionQueryDTO query);

    IPage<GoldQuantCommissionRecordVO> getAdminCommissionPage(AdminGoldQuantCommissionQueryDTO query);

    GoldQuantCommissionStatisticsVO getAdminCommissionStatistics(AdminGoldQuantCommissionQueryDTO query);

    IPage<AdminGoldQuantUserStatisticsVO> getAdminUserStatisticsPage(AdminGoldQuantUserStatisticsQueryDTO query);

    GoldQuantCommissionSettingsVO getRules();
}
