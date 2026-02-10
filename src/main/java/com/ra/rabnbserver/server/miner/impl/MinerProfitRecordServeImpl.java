package com.ra.rabnbserver.server.miner.impl;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.dto.MinerProfitRecordQueryDTO;
import com.ra.rabnbserver.mapper.MinerProfitRecordMapper;
import com.ra.rabnbserver.pojo.MinerProfitRecord;
import com.ra.rabnbserver.server.miner.MinerProfitRecordServe;
import org.springframework.stereotype.Service;

@Service
public class MinerProfitRecordServeImpl extends ServiceImpl<MinerProfitRecordMapper,MinerProfitRecord> implements MinerProfitRecordServe {
    @Override
    public IPage<MinerProfitRecord> findPage(MinerProfitRecordQueryDTO queryDTO) {
        // 初始化分页对象
        Page<MinerProfitRecord> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        // 构建查询条件
        LambdaQueryWrapper<MinerProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryDTO.getUserId() != null, MinerProfitRecord::getUserId, queryDTO.getUserId())
                .eq(StrUtil.isNotBlank(queryDTO.getWalletAddress()), MinerProfitRecord::getWalletAddress, queryDTO.getWalletAddress())
                .eq(StrUtil.isNotBlank(queryDTO.getMinerType()), MinerProfitRecord::getMinerType, queryDTO.getMinerType())
                .eq(queryDTO.getPayoutStatus() != null, MinerProfitRecord::getPayoutStatus, queryDTO.getPayoutStatus())
                .eq(queryDTO.getLockType() != null, MinerProfitRecord::getLockType, queryDTO.getLockType())
                .eq(queryDTO.getDistType() != null, MinerProfitRecord::getDistType, queryDTO.getDistType())
                .eq(StrUtil.isNotBlank(queryDTO.getTxId()), MinerProfitRecord::getTxId, queryDTO.getTxId());
        // 时间筛选：使用 Hutool 格式化
        // DateUtil.parse 能够解析 yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, yyyy/MM/dd 等多种格式
        if (StrUtil.isNotBlank(queryDTO.getStartTime())) {
            wrapper.ge(MinerProfitRecord::getCreateTime, DateUtil.parse(queryDTO.getStartTime()));
        }
        if (StrUtil.isNotBlank(queryDTO.getEndTime())) {
            // 如果是 yyyy-MM-dd 格式，通常希望查到当天 23:59:59，可以使用 DateUtil.endOfDay
            wrapper.le(MinerProfitRecord::getCreateTime, DateUtil.endOfDay(DateUtil.parse(queryDTO.getEndTime())));
        }
        // 排序：默认按创建时间倒序
        wrapper.orderByDesc(MinerProfitRecord::getCreateTime);
        return this.page(page, wrapper);
    }
}
