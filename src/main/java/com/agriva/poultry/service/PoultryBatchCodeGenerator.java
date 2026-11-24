package com.agriva.poultry.service;

import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.repository.PoultryBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates farm-scoped, year-scoped, human-readable batch codes.
 * <p>
 * Format: {@code BATCH-{YYYY}-{NNNNN}} — e.g. {@code BATCH-2026-00042}.
 * <p>
 * Uniqueness is enforced by the database via {@code (farm_id, batch_code)}.
 * Concurrent creates for the same farm+year can race; the caller
 * ({@code PoultryBatchServiceImpl#createBatch}) retries on collision.
 */
@Component
@RequiredArgsConstructor
public class PoultryBatchCodeGenerator {

    private static final String PREFIX = "BATCH";

    private final PoultryBatchRepository batchRepo;

    public String nextCode(UUID farmId) {
        int year = Year.now().getValue();
        String yearPrefix = PREFIX + "-" + year + "-";

        Optional<PoultryBatch> latest = batchRepo
                .findFirstByFarmIdAndBatchCodeStartingWithOrderByBatchCodeDesc(farmId, yearPrefix);

        int nextSeq = 1;
        if (latest.isPresent()) {
            String maxCode = latest.get().getBatchCode();
            if (maxCode != null && maxCode.length() > yearPrefix.length()) {
                String numericPart = maxCode.substring(yearPrefix.length());
                try {
                    nextSeq = Integer.parseInt(numericPart) + 1;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return String.format("%s%05d", yearPrefix, nextSeq);
    }
}