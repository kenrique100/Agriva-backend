package com.agriva.farming.utils;

import com.agriva.farming.model.entity.Crop;
import com.agriva.farming.model.enums.CropCycleStatus;
import com.agriva.shared.exception.BusinessException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CropCycleRules {

    public static final int DEFAULT_DAYS_TO_HARVEST = 90;

    private static final Map<CropCycleStatus, Set<CropCycleStatus>> ALLOWED = Map.of(
            CropCycleStatus.PLANNED,   EnumSet.of(CropCycleStatus.PLANTED, CropCycleStatus.FAILED),
            CropCycleStatus.PLANTED,   EnumSet.of(CropCycleStatus.GROWING, CropCycleStatus.FAILED),
            CropCycleStatus.GROWING,   EnumSet.of(CropCycleStatus.HARVESTED, CropCycleStatus.FAILED),
            CropCycleStatus.HARVESTED, EnumSet.noneOf(CropCycleStatus.class),
            CropCycleStatus.FAILED,    EnumSet.noneOf(CropCycleStatus.class)
    );

    private CropCycleRules() {}

    public static LocalDate expectedHarvestFor(Crop crop, LocalDate plantingDate) {
        int days = crop.getExpectedDaysToHarvest() != null
                ? crop.getExpectedDaysToHarvest()
                : DEFAULT_DAYS_TO_HARVEST;
        return plantingDate.plusDays(days);
    }

    public static boolean isAllowed(CropCycleStatus from, CropCycleStatus to) {
        if (from == to) return true;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(CropCycleStatus from, CropCycleStatus to) {
        if (!isAllowed(from, to)) {
            throw new BusinessException(
                    "Illegal crop-cycle status transition: " + from + " \u2192 " + to);
        }
    }
}