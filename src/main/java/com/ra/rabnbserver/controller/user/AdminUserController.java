package com.ra.rabnbserver.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.userServe;
import com.ra.rabnbserver.dto.UserQueryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    @Autowired
    private userServe userService;

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
        // 关键点：由 Service 层确保 balance 不被修改
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
}