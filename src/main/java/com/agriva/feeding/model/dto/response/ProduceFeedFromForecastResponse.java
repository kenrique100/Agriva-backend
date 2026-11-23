package com.agriva.feeding.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProduceFeedFromForecastResponse {

    private FormulationResponse formulation;
    private FeedBatchResponse feedBatch;
    private boolean replayed;
}