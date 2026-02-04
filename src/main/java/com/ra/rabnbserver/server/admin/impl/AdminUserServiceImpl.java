package com.ra.rabnbserver.server.admin.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.AdminUserMapper;
import com.ra.rabnbserver.pojo.AdminUser;
import com.ra.rabnbserver.server.admin.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {

    /**
     * 添加管理员
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean addAdmin(AdminUser adminUser) {
        // 校验用户名是否重复
        long count = count(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, adminUser.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 默认状态设为正常
        if (adminUser.getStatus() == null) {
            adminUser.setStatus(1);
        }
        return this.save(adminUser);
    }

    /**
     * 更新管理员
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateAdmin(AdminUser adminUser) {
        if (adminUser.getId() == null) return false;

        // 1. 如果修改了用户名，需要查重
        if (StrUtil.isNotBlank(adminUser.getUsername())) {
            AdminUser exist = this.getOne(new LambdaQueryWrapper<AdminUser>()
                    .eq(AdminUser::getUsername, adminUser.getUsername())
                    .ne(AdminUser::getId, adminUser.getId())); // 排除自己
            if (exist != null) {
                throw new BusinessException("用户名已存在，请更换");
            }
        }

        // 2. 执行更新
        return this.lambdaUpdate()
                .eq(AdminUser::getId, adminUser.getId())
                .set(StrUtil.isNotBlank(adminUser.getUsername()), AdminUser::getUsername, adminUser.getUsername())
                .set(StrUtil.isNotBlank(adminUser.getNickname()), AdminUser::getNickname, adminUser.getNickname())
                .set(StrUtil.isNotBlank(adminUser.getPassword()), AdminUser::getPassword, adminUser.getPassword()) // 允许修改密码
                .set(adminUser.getRoleId() != null, AdminUser::getRoleId, adminUser.getRoleId())
                .set(adminUser.getStatus() != null, AdminUser::getStatus, adminUser.getStatus())
                .update();
    }

}