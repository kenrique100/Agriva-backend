package com.agriva.poultry.service;

import com.agriva.poultry.model.dto.response.BatchFcrResponse;

import java.util.UUID;

public interface FcrService {

    /**
     * Feed Conversion Ratio for one batch.
     * <p>
     * FCR = total feed consumed (kg) / total live-weight gain (kg) since
     * arrival. Feed consumption is read from {@code feeding :: port}; the
     * poultry module never stores it.
     * <p>
     * Returns a response with {@code fcr = null} and a human-readable
     * {@code note} when FCR is not computable (multiple active batches in the
     * house, no weight samples, batch not active, …).
     */
    BatchFcrResponse compute(UUID batchId, UUID farmId);
}