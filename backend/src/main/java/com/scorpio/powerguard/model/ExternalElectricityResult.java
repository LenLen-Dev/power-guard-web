package com.scorpio.powerguard.model;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExternalElectricityResult {

    private BigDecimal total;
    private BigDecimal remain;
    private String rawResponse;
    private String message;
    private String account;
    private String roomName;
    private String buildingName;

    public ExternalElectricityResult(BigDecimal total, BigDecimal remain, String rawResponse) {
        this.total = total;
        this.remain = remain;
        this.rawResponse = rawResponse;
    }

    public ExternalElectricityResult(
        BigDecimal total,
        BigDecimal remain,
        String rawResponse,
        String message,
        String account,
        String roomName,
        String buildingName
    ) {
        this.total = total;
        this.remain = remain;
        this.rawResponse = rawResponse;
        this.message = message;
        this.account = account;
        this.roomName = roomName;
        this.buildingName = buildingName;
    }
}
