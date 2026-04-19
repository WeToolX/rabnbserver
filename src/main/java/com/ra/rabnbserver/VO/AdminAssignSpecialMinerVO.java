package com.ra.rabnbserver.VO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminAssignSpecialMinerVO {
    private Long userId;
    private Integer quantity;
    private List<Long> minerIds;
}
