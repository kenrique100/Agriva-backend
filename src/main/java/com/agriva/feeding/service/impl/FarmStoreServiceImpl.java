package com.agriva.feeding.service.impl;

import com.agriva.feeding.model.dto.response.FeedStoreAdjustmentResponse;
import com.agriva.feeding.model.entity.FarmFeedStore;
import com.agriva.feeding.model.entity.FeedStoreAdjustment;
import com.agriva.feeding.model.enums.StockAdjustmentType;
import com.agriva.feeding.repository.FarmFeedStoreRepository;
import com.agriva.feeding.repository.FeedStoreAdjustmentRepository;
import com.agriva.feeding.service.FarmStoreService;
import com.agriva.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmStoreServiceImpl implements FarmStoreService {

    private final FarmFeedStoreRepository storeRepo;
    private final FeedStoreAdjustmentRepository adjustmentRepo;

    @Override
    @Transactional
    public FarmFeedStore getOrCreateForUpdate(UUID farmId) {
        Optional<FarmFeedStore> existing = storeRepo.findByFarmIdForUpdate(farmId);
        if (existing.isPresent()) return existing.get();

        storeRepo.insertIfAbsent(UUID.randomUUID(), farmId);

        return storeRepo.findByFarmIdForUpdate(farmId)
                .orElseThrow(() -> new BusinessException(
                        "Farm store could not be acquired after insert (farm=" + farmId + ")"));
    }

    @Override
    @Transactional(readOnly = true)
    public FarmFeedStore get(UUID farmId) {
        return storeRepo.findByFarmId(farmId).orElse(null);
    }

    @Override
    @Transactional
    public FarmFeedStore save(FarmFeedStore store) {
        if (store.getQuantityOnHandKg() == null) {
            store.setQuantityOnHandKg(BigDecimal.ZERO);
        }
        return storeRepo.save(store);
    }

    @Override
    @Transactional
    public FarmFeedStore setAbsolute(UUID farmId, BigDecimal newQuantityKg,
                                     String reason, Long expectedVersion, UUID userId) {
        if (newQuantityKg == null || newQuantityKg.signum() < 0) {
            throw new BusinessException("quantityOnHandKg must be >= 0");
        }
        FarmFeedStore store = getOrCreateForUpdate(farmId);

        if (expectedVersion != null && !expectedVersion.equals(store.getVersion())) {
            throw new BusinessException(
                    "The farm store was modified by someone else (expected version "
                            + expectedVersion + ", current " + store.getVersion()
                            + "). Reload and try again.");
        }

        BigDecimal previous = store.getQuantityOnHandKg() == null
                ? BigDecimal.ZERO : store.getQuantityOnHandKg();

        store.setQuantityOnHandKg(newQuantityKg);
        FarmFeedStore saved = storeRepo.save(store);

        recordAdjustment(farmId, previous, newQuantityKg,
                StockAdjustmentType.ABSOLUTE_SET, reason, userId);

        log.info("Farm store SET {} -> {} kg on farm {} (reason='{}', by={})",
                previous, newQuantityKg, farmId, reason, userId);
        return saved;
    }

    @Override
    @Transactional
    public FarmFeedStore applyDelta(UUID farmId, BigDecimal deltaKg, String reason, UUID userId) {
        if (deltaKg == null || deltaKg.signum() == 0) {
            throw new BusinessException("deltaKg must be non-zero");
        }
        FarmFeedStore store = getOrCreateForUpdate(farmId);

        BigDecimal previous = store.getQuantityOnHandKg() == null
                ? BigDecimal.ZERO : store.getQuantityOnHandKg();
        BigDecimal target = previous.add(deltaKg);
        if (target.signum() < 0) {
            throw new BusinessException(
                    "Adjustment would make farm store negative (current "
                            + previous + " kg, delta " + deltaKg + " kg).");
        }

        store.setQuantityOnHandKg(target);
        FarmFeedStore saved = storeRepo.save(store);

        recordAdjustment(farmId, previous, target,
                StockAdjustmentType.DELTA, reason, userId);

        log.info("Farm store DELTA {} kg -> {} kg on farm {} (reason='{}', by={})",
                deltaKg, target, farmId, reason, userId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FeedStoreAdjustmentResponse> listAdjustments(UUID farmId, Pageable pageable) {
        return adjustmentRepo
                .findByFarmIdOrderByPerformedAtDesc(farmId, pageable)
                .map(this::toAdjustmentResponse);
    }

    private void recordAdjustment(UUID farmId, BigDecimal previous, BigDecimal target,
                                  StockAdjustmentType type, String reason, UUID userId) {
        adjustmentRepo.save(FeedStoreAdjustment.builder()
                .farmId(farmId)
                .previousQuantityKg(previous)
                .newQuantityKg(target)
                .deltaKg(target.subtract(previous))
                .reason(reason)
                .adjustmentType(type)
                .performedByUserId(userId)
                .build());
    }

    private FeedStoreAdjustmentResponse toAdjustmentResponse(FeedStoreAdjustment a) {
        return FeedStoreAdjustmentResponse.builder()
                .id(a.getId())
                .farmId(a.getFarmId())
                .previousQuantityKg(a.getPreviousQuantityKg())
                .newQuantityKg(a.getNewQuantityKg())
                .deltaKg(a.getDeltaKg())
                .reason(a.getReason())
                .adjustmentType(a.getAdjustmentType())
                .performedByUserId(a.getPerformedByUserId())
                .performedAt(a.getPerformedAt())
                .build();
    }
}