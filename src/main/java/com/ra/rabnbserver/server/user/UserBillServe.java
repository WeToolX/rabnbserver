package com.ra.rabnbserver.server.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.VO.AdminBillStatisticsVO;
import com.ra.rabnbserver.VO.CreateUserBillVO;
import com.ra.rabnbserver.VO.PaymentUsdtMetaVO;
import com.ra.rabnbserver.dto.AdminBillQueryDTO;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.pojo.UserBill;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface UserBillServe extends IService<UserBill> {
//    @Transactional(rollbackFor = Exception.class)
//    void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType,
//                                    FundType fundType, TransactionType txType,
//                                    String remark, String orderId, String txId,String res);


//    void createBillAndUpdateBalance(
//            Long userId,
//            BigDecimal amount,
//            BillType billType,
//            FundType fundType,
//            TransactionType txType,
//            String remark,
//            String orderId,
//            String txId,
//            String res,
//            int num
//    );

    IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query);

    void rechargeFromChain(Long userId, BigDecimal amount) throws Exception;

    void purchaseNftCard(Long userId, int quantity);

    void createBillAndUpdateBalance(
            Long userId,
            BigDecimal amount,
            BillType billType,
            FundType fundType,
            TransactionType txType,
            String remark,
            String orderId,
            String txId,
            String res,
            int num,
            CreateUserBillVO createUserBillVO
    );

    IPage<UserBill> getAdminBillPage(AdminBillQueryDTO query);

    AdminBillStatisticsVO getPlatformStatistics();

    PaymentUsdtMetaVO getPaymentUsdtMeta() throws Exception;

    void distributeNftByAdmin(Long userId, Integer amount);
}
