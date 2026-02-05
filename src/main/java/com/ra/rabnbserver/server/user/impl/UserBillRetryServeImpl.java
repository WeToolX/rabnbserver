package com.ra.rabnbserver.server.user.impl;

import com.ra.rabnbserver.enums.TransactionStatus;
import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.pojo.UserBill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "user_bill",
        serviceName = "账单链上操作重试",
        idField = "id",
        userField = "user_id",
        statusField = "status",
        successValue = "SUCCESS",
        failValue = "FAILED",
        minIntervalSeconds = 60,
        timeoutSeconds = 3600,
        maxRetryCount = 3,
        manualRemindIntervalSeconds = 300
)
public class UserBillRetryServeImpl extends AbstractAbnormalRetryService {

    private final UserBillMapper userBillMapper;

    public UserBillRetryServeImpl(AbnormalRetryManager abnormalRetryManager, UserBillMapper userBillMapper) {
        super(abnormalRetryManager);
        this.userBillMapper = userBillMapper;
    }

    @Override
    public boolean checkStatus(Long dataId) {
        UserBill bill = userBillMapper.selectById(dataId);
        return bill != null && TransactionStatus.SUCCESS.equals(bill.getStatus());
    }

    @Override
    public boolean ExceptionHandling(Long dataId) {
        // 这里的逻辑可以根据 txId 去链上轮询，或者根据业务重新发起请求
        log.info("异常框架：正在检查/重试账单数据 ID: {}", dataId);
        return false; // 链上操作建议由人工核实或特定轮询脚本处理，此处返回false触发持续提醒
    }

    // 提升访问权限供业务层调用
    @Override public void markAbnormal(Long dataId) { super.markAbnormal(dataId); }
    @Override public void markAbnormal(Long dataId, String userValue) { super.markAbnormal(dataId, userValue); }
    @Override public void checkUserErr(String userValue) { super.checkUserErr(userValue); }
    @Override public void ProcessingSuccessful(Long dataId) { super.ProcessingSuccessful(dataId); }
}