package com.ra.rabnbserver.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.AdminPermission;
import com.ra.rabnbserver.pojo.AdminRole;
import com.ra.rabnbserver.pojo.AdminUser;
import com.ra.rabnbserver.server.admin.AdminPermissionService;
import com.ra.rabnbserver.server.admin.AdminRoleService;
import com.ra.rabnbserver.server.admin.AdminUserService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 RBAC 权限管理控制器
 */
@RestController
@RequestMapping("/api/admin/rbac")
public class AdminRbacController {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private AdminRoleService adminRoleService;
    @Autowired
    private AdminPermissionService adminPermissionService;

    // ======================== 1. 当前管理员信息 ========================

    /**
     * 获取当前登录管理员的角色信息和权限列表
     */
    @GetMapping("/current-info")
    public String getCurrentInfo() {
        // 获取当前登录 ID
        long adminId = StpUtil.getLoginIdAsLong();
        AdminUser admin = adminUserService.getById(adminId);
        if (admin == null) return ApiResponse.error("账号异常");

        AdminRole role = adminRoleService.getById(admin.getRoleId());

        // 获取权限列表（含继承）
        List<String> permissions = adminRoleService.getPermissionsIncludingParents(admin.getRoleId());

        Map<String, Object> data = new HashMap<>();
        data.put("user", admin);
        data.put("role", role);
        data.put("permissions", permissions);

        return ApiResponse.success(data);
    }

    // ======================== 2. 管理员用户管理 (仅限超级管理员) ========================

    /**
     * 返回所有管理员列表
     * @return
     */
    @SaCheckRole("super_admin")
    @GetMapping("/user/list")
    public String listAdmins() {
        return ApiResponse.success(adminUserService.list());
    }

    /**
     * 添加管理员
     * @param adminUser
     * @return
     */
    @SaCheckRole("super_admin")
    @PostMapping("/user/add")
    public String addAdmin(@RequestBody AdminUser adminUser) {
        return adminUserService.addAdmin(adminUser) ? ApiResponse.success() : ApiResponse.error("添加失败");
    }

    /**
     * 更新管理员
     * @param adminUser
     * @return
     */
    @SaCheckRole("super_admin")
    @PutMapping("/user/update")
    public String updateAdmin(@RequestBody AdminUser adminUser) {
        return adminUserService.updateAdmin(adminUser) ? ApiResponse.success() : ApiResponse.error("修改失败");
    }

    /**
     * 删除管理员
     * @param id
     * @return
     */
    @SaCheckRole("super_admin")
    @GetMapping("/user/delete/{id}")
    public String deleteAdmin(@PathVariable Long id) {
        if (id == 1L) return ApiResponse.error("不能删除初始超级管理员");
        return adminUserService.removeById(id) ? ApiResponse.success() : ApiResponse.error("删除失败");
    }

    // ======================== 3. 角色管理 (仅限超级管理员) ========================

    /**
     * 查询角色列表
     * @return
     */
    @SaCheckRole("super_admin")
    @GetMapping("/role/list")
    public String listRoles() {
        return ApiResponse.success(adminRoleService.list());
    }

    /**
     * 添加角色
     * @param role
     * @return
     */
    @SaCheckRole("super_admin")
    @PostMapping("/role/add")
    public String addRole(@RequestBody AdminRole role) {
        return adminRoleService.save(role) ? ApiResponse.success() : ApiResponse.error("操作失败");
    }

    /**
     * 删除角色
     * @param id
     * @return
     */
    @SaCheckRole("super_admin")
    @GetMapping("/role/delete/{id}")
    public String deleteRole(@PathVariable Long id) {
        // ServiceImpl 中已重写 removeById 实现级联删除
        return adminRoleService.removeById(id) ? ApiResponse.success() : ApiResponse.error("操作失败");
    }

    /**
     * 获取角色列表（包含关联的权限ID列表）
     * 专供前端权限管理页面使用
     */
    @SaCheckRole("super_admin")
    @GetMapping("/role/list-with-perms")
    public String listRolesWithPerms() {
        return ApiResponse.success(adminRoleService.listRolesWithPermIds());
    }

    /**
     * 给角色分配权限
     */
    @SaCheckRole("super_admin")
    @PostMapping("/role/assign-permissions")
    public String assignPermissions(@RequestBody RolePermissionDTO dto) {
        adminRoleService.assignPermissions(dto.getRoleId(), dto.getPermissionIds());
        return ApiResponse.success("分配成功");
    }

    // ======================== 4. 权限项管理 (仅限超级管理员) ========================

    /**
     * 查询权限列表
     * @return
     */
    @SaCheckRole("super_admin")
    @GetMapping("/permission/list")
    public String listPermissions() {
        return ApiResponse.success(adminPermissionService.list());
    }

    /**
     * 添加权限
     * @param permission
     * @return
     */
    @SaCheckRole("super_admin")
    @PostMapping("/permission/add")
    public String addPermission(@RequestBody AdminPermission permission) {
        return adminPermissionService.save(permission) ? ApiResponse.success() : ApiResponse.error("操作失败");
    }

    /**
     * 删除权限
     * @param id
     * @return
     */
    @SaCheckRole("super_admin")
    @GetMapping("/permission/delete/{id}")
    public String deletePermission(@PathVariable Long id) {
        // ServiceImpl 中已重写 removeById 实现级联删除
        return adminPermissionService.removeById(id) ? ApiResponse.success() : ApiResponse.error("操作失败");
    }

    /**
     * DTO 内部类：用于权限分配接收参数
     */
    @Data
    public static class RolePermissionDTO {
        private Long roleId;
        private List<Long> permissionIds;
    }
}