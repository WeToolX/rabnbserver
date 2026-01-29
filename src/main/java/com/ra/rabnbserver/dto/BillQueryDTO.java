package com.ra.rabnbserver.dto;

import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionStatus;
import com.ra.rabnbserver.enums.TransactionType;
import lombok.Data;

@Data
public class BillQueryDTO {
    private Integer page = 1;      // 当前页码
    private Integer size = 10;     // 每页条数
    /**
     * 开始日期 格式：yyyy-MM-dd 00:00:00
     */
    private String startDate;
    /**
     * 结束日期 格式：yyyy-MM-dd 00:00:00
     */
    private String endDate;
    /**
     * 账单类型筛选
     */
    private BillType billType;
    /**
     * 交易类型筛选
     */
    private TransactionType transactionType;
    /**
     * 出入账类型筛选
     */
    private FundType fundType;
    /**
     * 交易状态
     */
    private TransactionStatus  transactionStatus;
}