package com.agriva.feeding.model.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateDemandForecastRequest extends DemandForecastRequest {

    private Long expectedVersion;
}