package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.dto.UserQueryDTO;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.UserServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@Slf4j
public class UserServeImpl extends ServiceImpl<UserMapper, User> implements UserServe {

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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean addUser(User user) {
        user.setBalance(BigDecimal.ZERO);
        return this.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUser(User user) {
        if (user.getId() == null) {
            return false;
        }
        return this.lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(user.getUserName() != null, User::getUserName, user.getUserName())
                .set(user.getUserWalletAddress() != null, User::getUserWalletAddress, user.getUserWalletAddress())
                .update();
    }

    @Override
    public IPage<User> selectUserPage(UserQueryDTO queryDTO) {
        Page<User> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(queryDTO.getUserWalletAddress()),
                User::getUserWalletAddress, queryDTO.getUserWalletAddress());
        if (StrUtil.isNotBlank(queryDTO.getStartTime())) {
            wrapper.ge(User::getCreateTime, DateUtil.parse(queryDTO.getStartTime()).toLocalDateTime());
        }
        if (StrUtil.isNotBlank(queryDTO.getEndTime())) {
            wrapper.le(User::getCreateTime, DateUtil.parse(queryDTO.getEndTime()).toLocalDateTime());
        }
        wrapper.orderByDesc(User::getCreateTime);
        return this.page(page, wrapper);
    }
}
