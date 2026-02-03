package com.ra.rabnbserver.config;

import cn.dev33.satoken.stp.StpInterface;
import com.ra.rabnbserver.pojo.AdminRole;
import com.ra.rabnbserver.pojo.AdminUser;
import com.ra.rabnbserver.server.admin.AdminRoleService;
import com.ra.rabnbserver.server.admin.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义权限验证接口扩展
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private AdminRoleService adminRoleService;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1. 获取管理员信息
        AdminUser admin = adminUserService.getById(Long.parseLong(loginId.toString()));
        if (admin == null || admin.getRoleId() == null) {
            return new ArrayList<>();
        }

        // 2. 获取角色信息
        AdminRole role = adminRoleService.getById(admin.getRoleId());
        if (role == null) {
            return new ArrayList<>();
        }

        // 3. 【核心逻辑】如果是超级管理员，直接返回通配符 "*"
        // Sa-Token 默认支持 "*" 匹配所有权限
        if ("super_admin".equals(role.getRoleKey())) {
            return Collections.singletonList("*");
        }

        // 4. 普通管理员：返回其角色及继承的所有权限
        return adminRoleService.getPermissionsIncludingParents(admin.getRoleId());
    }

    /**
     * 返回一个账号所拥有的角色标识集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AdminUser admin = adminUserService.getById(Long.parseLong(loginId.toString()));
        if (admin == null || admin.getRoleId() == null) {
            return new ArrayList<>();
        }

        AdminRole role = adminRoleService.getById(admin.getRoleId());
        if (role == null) {
            return new ArrayList<>();
        }

        return Collections.singletonList(role.getRoleKey());
    }
}