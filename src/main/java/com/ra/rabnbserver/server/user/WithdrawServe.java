package com.ra.rabnbserver.server.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.user.WithdrawAuditDTO;
import com.ra.rabnbserver.dto.withdraw.AdminWithdrawQueryDTO;
import com.ra.rabnbserver.dto.withdraw.UserWithdrawQueryDTO;
import com.ra.rabnbserver.pojo.WithdrawRecord;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface WithdrawServe extends IService<WithdrawRecord> {
    @Transactional(rollbackFor = Exception.class)
    void applyWithdraw(Long userId, BigDecimal amount);

    @Transactional(rollbackFor = Exception.class)
    void auditWithdraw(WithdrawAuditDTO dto);

    IPage<WithdrawRecord> getUserWithdrawPage(Long userId, UserWithdrawQueryDTO query);

    IPage<WithdrawRecord> getAdminWithdrawPage(AdminWithdrawQueryDTO query);
}
