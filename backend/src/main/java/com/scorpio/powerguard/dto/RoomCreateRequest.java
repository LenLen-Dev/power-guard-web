package com.scorpio.powerguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class RoomCreateRequest {

    @NotBlank(message = "buildingId不能为空")
    private String buildingId;

    @NotBlank(message = "buildingName不能为空")
    private String buildingName;

    @NotBlank(message = "roomId不能为空")
    private String roomId;

    @NotBlank(message = "roomName不能为空")
    private String roomName;

    @Email(message = "alertEmail格式不正确")
    private String alertEmail;

    @NotNull(message = "threshold不能为空")
    @DecimalMin(value = "0", message = "threshold不能小于0")
    private BigDecimal threshold;
}
