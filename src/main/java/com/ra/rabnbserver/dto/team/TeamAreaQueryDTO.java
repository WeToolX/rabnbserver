package com.ra.rabnbserver.dto.team;

import lombok.Data;

@Data
public class TeamAreaQueryDTO {
    /**
     * 1-大区, 2-小区
     */
    private Integer type;

    private Integer page = 1;

    private Integer size = 20;
}
