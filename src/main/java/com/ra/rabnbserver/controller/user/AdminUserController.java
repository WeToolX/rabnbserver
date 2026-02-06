package com.ra.rabnbserver.controller.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.dto.*;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.AdminPermission;
import com.ra.rabnbserver.pojo.AdminRole;
import com.ra.rabnbserver.pojo.AdminUser;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.admin.AdminPermissionService;
import com.ra.rabnbserver.server.admin.AdminRoleService;
import com.ra.rabnbserver.server.admin.AdminUserService;
import com.ra.rabnbserver.server.user.UserBillServe;
import com.ra.rabnbserver.server.user.UserServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;

/**
 * 管理员 - 用户管理
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    @Autowired
    private UserServe userService;

    private final CardNftContract cardNftContract;
    private final UserBillServe userBillServer;
    private final AdminUserService adminUserService;
    private final AdminRoleService adminRoleService;
    private final AdminPermissionService adminPermissionService;
    private final UserBillServe userBillServe;

    public AdminUserController(CardNftContract cardNftContract, UserBillServe userBillServer, AdminUserService adminUserService, AdminRoleService adminRoleService, AdminPermissionService adminPermissionService, UserBillServe userBillServe) {
        this.cardNftContract = cardNftContract;
        this.userBillServer = userBillServer;
        this.adminUserService = adminUserService;
        this.adminRoleService = adminRoleService;
        this.adminPermissionService = adminPermissionService;
        this.userBillServe = userBillServe;
    }

    @Value("${ADMIN.USERNAME}")
    private String AdminUserName;
    @Value("${ADMIN.PASSWORD}")
    private String AdminPassword;



    /**
     * 管理用户登录
     * 逻辑：若管理员表为空，则自动初始化超级管理员数据
     */
    @PostMapping("/admin/login")
    @Transactional(rollbackFor = Exception.class)
    public String adminLogin(@RequestBody AdminUserLoginDTO adminUserLoginDTO) {
        if (adminUserLoginDTO == null || adminUserLoginDTO.getUsername() == null || adminUserLoginDTO.getPassword() == null) {
            return ApiResponse.error("用户名或密码不能为空");
        }
        if (adminUserService.count() == 0) {
            log.info("检测到管理员表为空，正在初始化超级管理员数据...");
            initSuperAdminSystem();
        }
        AdminUser admin = adminUserService.getOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, adminUserLoginDTO.getUsername()));
        if (admin != null && admin.getPassword().equals(adminUserLoginDTO.getPassword())) {
            if (admin.getStatus() == 0) {
                return ApiResponse.error("该账号已被禁用");
            }
            StpUtil.login(admin.getId());
            return ApiResponse.success(StpUtil.getTokenInfo());
        } else {
            return ApiResponse.error("账号或密码错误");
        }
    }

    /**
     * 初始化 RBAC 数据私有方法
     */
    private void initSuperAdminSystem() {
        AdminRole superRole = new AdminRole();
        superRole.setRoleName("超级管理员");
        superRole.setRoleKey("super_admin");
        superRole.setParentId(0L);
        adminRoleService.save(superRole);
        AdminRole superRole2 = new AdminRole();
        superRole.setRoleName("管理员");
        superRole.setRoleKey("admin");
        superRole.setParentId(0L);
        adminRoleService.save(superRole2);
        AdminPermission allPerm = new AdminPermission();
        allPerm.setName("所有权限");
        allPerm.setPermKey("*"); // 标识拥有所有权限
        adminPermissionService.save(allPerm);
        adminRoleService.assignPermissions(superRole.getId(), Collections.singletonList(allPerm.getId()));
        AdminUser superUser = new AdminUser();
        superUser.setUsername(AdminUserName);
        superUser.setPassword(AdminPassword);
        superUser.setNickname("系统管理员");
        superUser.setRoleId(superRole.getId());
        superUser.setStatus(1);
        adminUserService.save(superUser);
        log.info("超级管理员初始化成功: {}", AdminUserName);
    }
    /**
     * 分页查询用户
     */
    @GetMapping("/list")
    public String list(UserQueryDTO queryDTO) {
        return ApiResponse.success(userService.selectUserPage(queryDTO));
    }

    /**
     * 添加用户
     */
    @PostMapping("/add")
    public String add(@RequestBody User user) {
        boolean saved = userService.addUser(user);
        return saved ? ApiResponse.success() : ApiResponse.error("添加失败");
    }

    /**
     * 修改用户
     */
    @PutMapping("/update")
    public String update(@RequestBody User user) {
        boolean updated = userService.updateUser(user);
        return updated ? ApiResponse.success() : ApiResponse.error("修改失败");
    }

    /**
     * 修改当前登录管理员自己的资料（用户名、密码、昵称）
     */
    @PostMapping("/profile/update")
    public String updateMyProfile(@RequestBody AdminUser user) {
        Long currentId = StpUtil.getLoginIdAsLong();
        user.setId(currentId);
        user.setRoleId(null);
        user.setStatus(null);

        boolean updated = adminUserService.updateAdmin(user);
        return updated ? ApiResponse.success("修改成功，下次登录生效") : ApiResponse.error("修改失败");
    }

    /**
     * 删除用户
     */
    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        boolean removed = userService.deleteUserWithCascade(id);
        return removed ? ApiResponse.success("删除成功，该用户的下级已重置为根用户") : ApiResponse.error("删除失败");
    }



    /**
     * 查询特定用户在链上的卡牌余额
     */
    @GetMapping("/chain-nft-balance")
    public String getChainBalance(@RequestParam Long userId) throws Exception {
        User user = userService.getById(userId);
        if (user == null) return ApiResponse.error("用户不存在");

        BigInteger balance = cardNftContract.balanceOf(user.getUserWalletAddress());
        return ApiResponse.success(balance);
    }

    /**
     * 管理员给用户发放卡牌 (手动分发)
     */
    @PostMapping("/distribute-nft")
    public String distributeNft(@RequestBody DistributeNftDTO dto) {
        userBillServer.distributeNftByAdmin(dto.getUserId(), dto.getAmount());
        return ApiResponse.success("分发指令已执行");
    }



    /**
     * 管理员给指定用户充值
     */
    @SaCheckLogin
    @PostMapping("/amount/deposit")
    public String deposit(@RequestBody AdminAmountRequestDTO dto) throws Exception {
        BigDecimal amount = new BigDecimal(dto.getAmount());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.error("充值金额必须大于0");
        }
        try {
            // 调用统一方法：平台类型、入账类型、充值业务
            userBillServe.createBillAndUpdateBalance(
                    dto.getUserId(),
                    amount,
                    BillType.PLATFORM,
                    FundType.INCOME,
                    TransactionType.DEPOSIT,
                    "系统充值",
                    null, // orderId 为空则内部自动生成
                    null,  // 平台内充值通常无链上 TxHash
                    null,
                    0
            );
            return ApiResponse.success("充值成功");
        } catch (BusinessException e) {
            log.error("充值失败: {}", e.getMessage());
            return ApiResponse.error("充值失败: " + e.getMessage());
        }
    }
}