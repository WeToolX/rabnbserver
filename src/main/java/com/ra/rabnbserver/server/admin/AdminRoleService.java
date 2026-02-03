package com.ra.rabnbserver.server.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.VO.AdminRoleVO;
import com.ra.rabnbserver.pojo.AdminRole;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AdminRoleService extends IService<AdminRole> {
    List<AdminRoleVO> listRolesWithPermIds();

    @Transactional(rollbackFor = Exception.class)
    void assignPermissions(Long roleId, List<Long> permissionIds);

    List<String> getPermissionsIncludingParents(Long roleId);
}
