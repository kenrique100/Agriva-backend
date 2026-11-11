package com.agriva.poultry.model.enums;

public enum PoultryBatchStatus {
    /** Currently housed — contributes to house headcount. */
    ACTIVE,
    /** Flock harvested / moved out. Does NOT contribute to headcount. */
    CLOSED,
    /** Flock culled. Does NOT contribute to headcount. */
    CULLED
}