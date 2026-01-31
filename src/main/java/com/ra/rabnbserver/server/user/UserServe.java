package com.ra.rabnbserver.server.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.dto.UserQueryDTO;
import com.ra.rabnbserver.pojo.User;
import org.springframework.transaction.annotation.Transactional;

public interface UserServe extends IService<User> {
    User getByWalletAddress(String address);

    @Transactional(rollbackFor = Exception.class)
    User register(String walletAddress);

    @Transactional(rollbackFor = Exception.class)
    boolean addUser(User user);

    @Transactional(rollbackFor = Exception.class)
    boolean updateUser(User user);

    IPage<User> selectUserPage(UserQueryDTO queryDTO);
}
