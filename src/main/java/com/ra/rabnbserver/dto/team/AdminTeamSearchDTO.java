package com.ra.rabnbserver.dto.team;

import com.ra.rabnbserver.dto.user.UserQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminTeamSearchDTO extends UserQueryDTO {


    /**
     * 领导人的钱包地址 (如果传了这个，就查这个地址名下的所有团队)
     */
    private String leaderWalletAddress;

    /**
     * 用户名查询
     */
    private String userName;

    /**
     * 领导人ID（查询该用户下属的所有团队成员）
     */
    private Long leaderId;

    /**
     * 直接上级ID（查询该用户的直推下级）
     */
    private Long parentId;

    /**
     * 最小团队人数
     */
    private Integer minTeamCount;

    /**
     * 最大团队人数
     */
    private Integer maxTeamCount;

    /**
     * 最小直推人数
     */
    private Integer minDirectCount;

    /**
     * 指定层级
     */
    private Integer level;

    /**
     * 邀请码
     */
    private String inviteCode;
}