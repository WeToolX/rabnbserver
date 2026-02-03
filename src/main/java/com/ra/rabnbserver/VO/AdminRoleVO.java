package com.ra.rabnbserver.VO;

import com.ra.rabnbserver.pojo.AdminRole;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminRoleVO extends AdminRole {
    /**
     * 该角色直接关联的权限ID列表
     */
    private List<Long> permissionIds;
}