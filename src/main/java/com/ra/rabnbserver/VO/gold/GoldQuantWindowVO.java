package com.ra.rabnbserver.VO.gold;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GoldQuantWindowVO {
    private Long id;
    private String title;
    private String windowNo;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime maintenanceExpireTime;
}
