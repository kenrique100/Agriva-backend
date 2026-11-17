package com.agriva.poultry.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PoultryHouseRequest {

    @NotBlank(message = "House name is required")
    @Size(max = 100, message = "House name must not exceed 100 characters")
    private String name;

    @Size(max = 255)
    private String location;

    @Positive(message = "Capacity must be positive")
    private Integer capacity;
}