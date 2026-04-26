package com.ra.rabnbserver.VO.gold;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GoldQuantHomeVO {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime hostingExpireTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nearestWindowMaintenanceExpireTime;
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal hostingFee = BigDecimal.ZERO;
    private BigDecimal windowMaintenanceFee = BigDecimal.ZERO;
    private Integer minerCount = 0;
    private Integer windowCount = 0;
    private Integer maxCanBuyCount = 0;
    private List<GoldQuantWindowVO> windows = new ArrayList<>();
}
