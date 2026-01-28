package com.ra.rabnbserver.server.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.pojo.User;
import org.springframework.transaction.annotation.Transactional;

public interface userServe extends IService<User> {
    User getByWalletAddress(String address);

    @Transactional(rollbackFor = Exception.class)
    User register(String walletAddress);
}
