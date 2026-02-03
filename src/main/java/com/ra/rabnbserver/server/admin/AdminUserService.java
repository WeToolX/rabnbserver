package com.ra.rabnbserver.server.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.pojo.AdminUser;
import org.springframework.transaction.annotation.Transactional;

public interface AdminUserService extends IService<AdminUser> {
    @Transactional(rollbackFor = Exception.class)
    boolean addAdmin(AdminUser adminUser);

    @Transactional(rollbackFor = Exception.class)
    boolean updateAdmin(AdminUser adminUser);
}
