package com.ra.rabnbserver.dto;

import lombok.Data;

import java.util.List;

/**
 * Miner electricity action request.
 */
@Data
public class MinerElectricityActionDTO {
    /**
     * IDS: selected miner ids.
     * TYPE: all matched miners by miner type.
     * ALL: all matched miners.
     */
    private String actionType;

    /**
     * Required when actionType=IDS. Values are user_miner.id from miner list.
     */
    private List<Long> userMinerIds;

    /**
     * Required when actionType=TYPE. Values: 0/1/2/3.
     */
    private String minerType;

    /**
     * Optional for TYPE/ALL. Empty or <= 0 means no limit.
     */
    private Integer quantity;

    /**
     * Renew only.
     * ALL_RENEWABLE: status in (1, 2)
     * ACTIVE: status = 1
     * EXPIRING_SOON: active miners expiring within 5 days
     * EXPIRED: expired miners
     */
    private String renewScope;
}
