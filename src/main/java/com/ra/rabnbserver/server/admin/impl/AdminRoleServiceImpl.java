package com.ra.rabnbserver.server.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.AdminRoleVO;
import com.ra.rabnbserver.mapper.AdminPermissionMapper;
import com.ra.rabnbserver.mapper.AdminRoleMapper;
import com.ra.rabnbserver.mapper.AdminRolePermissionMapper;
import com.ra.rabnbserver.pojo.AdminPermission;
import com.ra.rabnbserver.pojo.AdminRole;
import com.ra.rabnbserver.pojo.AdminRolePermission;
import com.ra.rabnbserver.server.admin.AdminRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements AdminRoleService {

    @Autowired
    private AdminRolePermissionMapper rolePermissionMapper;
    @Autowired
    private AdminPermissionMapper permissionMapper;

    /**
     * 重写删除方法，级联清理中间表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        // 1. 删除角色主表
        boolean removed = super.removeById(id);
        if (removed) {
            // 2. 清理角色-权限中间表
            rolePermissionMapper.delete(new LambdaQueryWrapper<AdminRolePermission>()
                    .eq(AdminRolePermission::getRoleId, id));
        }
        return removed;
    }

    @Override
    public List<AdminRoleVO> listRolesWithPermIds() {
        // 1. 获取所有角色
        List<AdminRole> roles = this.list();

        // 2. 获取所有的角色-权限映射关系 (一次性查出，避免循环查数据库)
        List<AdminRolePermission> allMappings = rolePermissionMapper.selectList(null);

        // 3. 组装数据
        return roles.stream().map(role -> {
            AdminRoleVO vo = new AdminRoleVO();
            // 使用 Spring 的 BeanUtils 拷贝基础属性
            org.springframework.beans.BeanUtils.copyProperties(role, vo);

            // 过滤出当前角色的权限ID
            List<Long> pIds = allMappings.stream()
                    .filter(m -> m.getRoleId().equals(role.getId()))
                    .map(AdminRolePermission::getPermissionId)
                    .collect(Collectors.toList());

            vo.setPermissionIds(pIds);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 为角色分配权限
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 先删除旧关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<AdminRolePermission>()
                .eq(AdminRolePermission::getRoleId, roleId));

        // 插入新关联
        for (Long pId : permissionIds) {
            AdminRolePermission arp = new AdminRolePermission();
            arp.setRoleId(roleId);
            arp.setPermissionId(pId);
            rolePermissionMapper.insert(arp);
        }
    }

    /**
     * 获取当前角色及其父级角色的所有权限 Key (用于 Sa-Token)
     */
    @Override
    public List<String> getPermissionsIncludingParents(Long roleId) {
        Set<Long> allRoleIds = new HashSet<>();

        // 递归查找父角色
        Long currentId = roleId;
        while (currentId != null && currentId != 0) {
            allRoleIds.add(currentId);
            AdminRole role = this.getById(currentId);
            currentId = (role != null) ? role.getParentId() : null;
        }

        if (allRoleIds.isEmpty()) return new ArrayList<>();

        // 查询所有涉及到的权限 ID
        List<AdminRolePermission> relations = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<AdminRolePermission>().in(AdminRolePermission::getRoleId, allRoleIds)
        );

        List<Long> pIds = relations.stream().map(AdminRolePermission::getPermissionId).distinct().collect(Collectors.toList());
        if (pIds.isEmpty()) return new ArrayList<>();

        // 获取权限 Key 列表
        return permissionMapper.selectList(new LambdaQueryWrapper<AdminPermission>().in(AdminPermission::getId, pIds))
                .stream().map(AdminPermission::getPermKey).collect(Collectors.toList());
    }
}