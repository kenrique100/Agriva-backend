package com.agriva.feeding.model.enums;

public enum StockAdjustmentType {
    /** Stock set to a specific value (physical count, system reset). */
    ABSOLUTE_SET,
    /** Stock changed by a signed delta (correction, found surplus). */
    DELTA,
    /** Stock deducted for a loss reason (spoilage, spillage, sampling). */
    WRITEOFF
}