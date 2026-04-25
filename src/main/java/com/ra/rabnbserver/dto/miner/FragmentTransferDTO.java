package com.ra.rabnbserver.dto.miner;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FragmentTransferDTO {
    private String toAddress;

    private BigDecimal amount;
}
