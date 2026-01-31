package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class TeamQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    /**
     * 相对层级：1-第一层(直推), 2-第二层... 不传则查所有下级
     */
    private Integer targetLevel;
}