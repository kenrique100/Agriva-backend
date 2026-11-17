package com.agriva.feeding.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FarmStoreResponse {

    private UUID farmId;
    private BigDecimal quantityOnHandKg;
    private BigDecimal bagSizeKg;
    private Integer fullBags;
    private BigDecimal remainderKg;
    private LocalDateTime lastUpdatedAt;
}