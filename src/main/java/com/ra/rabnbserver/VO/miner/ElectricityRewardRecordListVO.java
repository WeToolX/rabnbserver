package com.ra.rabnbserver.VO.miner;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.pojo.UserBill;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ElectricityRewardRecordListVO {
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private Integer teamTotalCount = 0;
    private IPage<UserBill> records;
}
