package com.ra.rabnbserver.dto.team;

import lombok.Data;

import java.util.List;

@Data
public class TeamBatchOperationDTO {
    /**
     * 需要操作的用户ID列表
     */
    private List<Long> userIds;

    /**
     * 目标上级ID (如果是解绑，则此字段可不传或传0)
     */
    private Long targetParentId;
}