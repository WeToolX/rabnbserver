package com.ra.rabnbserver.controller.user;

import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.dto.DistributeNftDTO;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.UserBillServe;
import com.ra.rabnbserver.server.user.UserServe;
import com.ra.rabnbserver.dto.UserQueryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

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

    public AdminUserController(CardNftContract cardNftContract, UserBillServe userBillServer) {
        this.cardNftContract = cardNftContract;
        this.userBillServer = userBillServer;
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
     * 删除用户
     */
    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        boolean removed = userService.removeById(id);
        return removed ? ApiResponse.success() : ApiResponse.error("删除失败");
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
}