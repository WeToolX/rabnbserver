package com.ra.rabnbserver.server.miner;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.MinerProfitRecordQueryDTO;
import com.ra.rabnbserver.pojo.MinerProfitRecord;

public interface MinerProfitRecordServe extends IService<MinerProfitRecord> {
    IPage<MinerProfitRecord> findPage(MinerProfitRecordQueryDTO queryDTO);
}
