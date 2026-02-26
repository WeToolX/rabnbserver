package com.ra.rabnbserver.dto.admin.bill;

import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionStatus;
import com.ra.rabnbserver.enums.TransactionType;
import lombok.Data;

@Data
public class AdminBillQueryDTO {
    private Integer page = 1;
    private Integer size = 10;

    // 过滤条件
    private String userWalletAddress; // 用户钱包地址 (支持模糊查询)
    private BillType billType;         // 账单类型
    private FundType fundType;         // 资金类型
    private TransactionType transactionType; // 交易业务类型
    private TransactionStatus status;  // 交易状态

    // 时间范围
    private String startDate;          // 开始日期 yyyy-MM-dd
    private String endDate;            // 结束日期 yyyy-MM-dd
}