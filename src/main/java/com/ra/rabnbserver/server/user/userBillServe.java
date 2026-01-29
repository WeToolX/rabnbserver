package com.ra.rabnbserver.server.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.pojo.UserBill;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface userBillServe extends IService<UserBill> {
    @Transactional(rollbackFor = Exception.class)
    void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType,
                                    FundType fundType, TransactionType txType,
                                    String remark, String orderId, String txId);

    IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query);
}
