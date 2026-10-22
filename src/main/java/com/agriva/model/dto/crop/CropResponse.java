package com.agriva.model.dto.crop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropResponse {
    private UUID id;
    private String name;
    private String variety;
    private Integer expectedDaysToHarvest;
}