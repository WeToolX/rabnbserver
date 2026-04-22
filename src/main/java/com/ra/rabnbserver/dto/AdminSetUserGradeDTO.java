package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class AdminSetUserGradeDTO {
    private Long userId;
    /**
     * 传入 0 或 null 将清除手动设置的等级
     */
    private Integer customUserGrade;
}
