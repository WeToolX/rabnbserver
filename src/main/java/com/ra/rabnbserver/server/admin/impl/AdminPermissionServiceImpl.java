package com.ra.rabnbserver.server.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.mapper.AdminPermissionMapper;
import com.ra.rabnbserver.mapper.AdminRolePermissionMapper;
import com.ra.rabnbserver.pojo.AdminPermission;
import com.ra.rabnbserver.pojo.AdminRolePermission;
import com.ra.rabnbserver.server.admin.AdminPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;

@Service
public class AdminPermissionServiceImpl extends ServiceImpl<AdminPermissionMapper, AdminPermission> implements AdminPermissionService {

    @Autowired
    private AdminRolePermissionMapper rolePermissionMapper;

    /**
     * 重写删除方法，实现级联删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        // 1. 删除权限主表
        boolean removed = super.removeById(id);
        if (removed) {
            // 2. 级联删除：清理角色-权限中间表中的关联关系
            rolePermissionMapper.delete(new LambdaQueryWrapper<AdminRolePermission>()
                    .eq(AdminRolePermission::getPermissionId, id));
        }
        return removed;
    }
}