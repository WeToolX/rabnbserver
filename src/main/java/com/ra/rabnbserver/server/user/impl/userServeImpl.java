package com.ra.rabnbserver.server.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.userServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
public class userServeImpl extends ServiceImpl<UserMapper, User> implements userServe {

    @Override
    public User getByWalletAddress(String address) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserWalletAddress, address));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User register(String walletAddress) {
        User user = new User();
        user.setUserWalletAddress(walletAddress);
        user.setUserName(walletAddress);
        user.setBalance(BigDecimal.ZERO);
        this.save(user);
        return user;
    }
}
