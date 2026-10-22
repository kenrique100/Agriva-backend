package com.agriva.mapper;

import com.agriva.model.dto.crop.*;
import com.agriva.model.entity.*;
import org.springframework.stereotype.Component;


@Component
public class CropMapper {
    public FieldResponse toResponse(Field f) {
        return new FieldResponse(f.getId(), f.getName(), f.getSizeHectares(), f.getSoilType());
    }
    public CropResponse toResponse(Crop c) {
        return new CropResponse(c.getId(), c.getName(), c.getVariety(), c.getExpectedDaysToHarvest());
    }
    public CropCycleResponse toResponse(CropCycle cc) {
        return new CropCycleResponse(
                cc.getId(), cc.getField().getId(), cc.getField().getName(),
                cc.getCrop().getId(), cc.getCrop().getName(),
                cc.getPlantingDate(), cc.getExpectedHarvestDate(), cc.getActualHarvestDate(),
                cc.getStatus().name(), cc.getNotes()
        );
    }
    public CropActivityResponse toResponse(CropActivity ca) {
        return new CropActivityResponse(
                ca.getId(), ca.getCropCycle().getId(),
                ca.getActivityType().name(), ca.getActivityDate(), ca.getNotes(),
                ca.getLinkedExpenseId(), ca.getLinkedTaskId()
        );
    }
    public HarvestResponse toResponse(Harvest h) {
        return new HarvestResponse(
                h.getId(), h.getCropCycle().getId(), h.getHarvestDate(),
                h.getQuantity(), h.getUnit(), h.getQualityGrade(), h.getLinkedIncomeId()
        );
    }
}