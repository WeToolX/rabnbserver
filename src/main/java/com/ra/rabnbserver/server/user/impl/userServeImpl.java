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
import com.ra.rabnbserver.server.user.userServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean addUser(User user) {
        // 强制设置余额为 0，防止通过接口注入余额
        user.setBalance(BigDecimal.ZERO);
        return this.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUser(User user) {
        // 更新时，创建一个新对象只设置允许修改的字段，或者将 balance 设为 null
        // MyBatis-Plus 默认 updateById 不会更新 null 字段
        user.setBalance(null);
        return this.updateById(user);
    }

    @Override
    public IPage<User> selectUserPage(UserQueryDTO queryDTO) {
        Page<User> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 模糊查询钱包地址
        wrapper.like(StringUtils.hasText(queryDTO.getUserWalletAddress()),
                User::getUserWalletAddress, queryDTO.getUserWalletAddress());

        // 2. 注册时间筛选（兼容多种格式）
        if (StrUtil.isNotBlank(queryDTO.getStartTime())) {
            // DateUtil.parse 会尝试多种格式解析，并转为 LocalDateTime
            wrapper.ge(User::getCreateTime, DateUtil.parse(queryDTO.getStartTime()).toLocalDateTime());
        }

        if (StrUtil.isNotBlank(queryDTO.getEndTime())) {
            // 解析结束时间
            wrapper.le(User::getCreateTime, DateUtil.parse(queryDTO.getEndTime()).toLocalDateTime());
        }
        wrapper.orderByDesc(User::getCreateTime);

        return this.page(page, wrapper);
    }
}
