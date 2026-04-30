package com.ra.rabnbserver.VO.gold;

import lombok.Data;

import java.util.List;

@Data
public class GoldQuantTeamAreaResultVO {
    private List<GoldQuantTeamAreaVO> records;
    private List<GoldQuantTeamAreaVO> directRecords;
}
