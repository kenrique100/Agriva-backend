package com.agriva.feeding.service.impl;

import com.agriva.feeding.config.FeedingProperties;
import com.agriva.feeding.mapper.FeedBatchMapper;
import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.response.FarmStoreResponse;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.entity.*;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.ProductionService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionServiceImpl implements ProductionService {

    private static final int SCALE_CALC = 8;
    private static final int SCALE_QTY  = 4;

    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository formulationLineRepository;
    private final IngredientStockRepository stockRepository;
    private final FeedBatchRepository feedBatchRepository;
    private final FarmFeedStoreRepository farmStoreRepository;
    private final FeedBatchMapper mapper;
    private final FeedingProperties feedingProperties;

    @Override
    @Transactional
    public FeedBatchResponse produce(ProductionRequest req, UUID farmId, UUID userId) {
        // 1. Load formulation + lines
        Formulation formulation = formulationRepository
                .findByIdAndFarmIdLive(req.getFormulationId(), farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + req.getFormulationId()));

        List<FormulationLine> lines = formulationLineRepository
                .findByFormulationIdOrderByPercentageDesc(formulation.getId());
        if (lines.isEmpty()) {
            throw new BusinessException("Formulation has no lines; cannot produce");
        }

        // 2. Scale factor — base is totalKg (the actual recipe sum), so
        //    sum(required_ingredients) == producedKg exactly, independent of
        //    whether targetQuantityKg matches totalKg. See class-level notes.
        BigDecimal baseTotal = formulation.getTotalKg();
        if (baseTotal == null || baseTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Formulation totalKg must be positive; cannot compute production scale");
        }
        BigDecimal producedKg = req.getProducedKg();
        BigDecimal scaleFactor = producedKg.divide(baseTotal, SCALE_CALC, RoundingMode.HALF_UP);

        // 3. Required per ingredient, keyed by ingredientId (order preserved by sorted list)
        Map<UUID, BigDecimal> requiredByIngredient = new LinkedHashMap<>();
        Map<UUID, String> nameById = new HashMap<>();
        for (FormulationLine line : lines) {
            BigDecimal required = line.getQuantityKg()
                    .multiply(scaleFactor)
                    .setScale(SCALE_QTY, RoundingMode.HALF_UP);
            requiredByIngredient.merge(line.getIngredientId(), required, BigDecimal::add);
            nameById.put(line.getIngredientId(), line.getIngredientName());
        }

        // 4. Lock ingredient stocks in ascending id order — deterministic across
        //    concurrent transactions, prevents lock-order deadlocks.
        List<UUID> sortedIds = requiredByIngredient.keySet().stream()
                .sorted()
                .toList();
        List<IngredientStock> lockedStocks =
                stockRepository.findAllForUpdate(farmId, sortedIds);
        Map<UUID, IngredientStock> stockById = lockedStocks.stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, s -> s));

        // 5. Stock check (fail with the first shortfall)
        for (UUID id : sortedIds) {
            BigDecimal required = requiredByIngredient.get(id);
            IngredientStock stock = stockById.get(id);
            BigDecimal available = stock == null || stock.getQuantityOnHand() == null
                    ? BigDecimal.ZERO
                    : stock.getQuantityOnHand();
            if (available.compareTo(required) < 0) {
                throw new InsufficientIngredientQuantityException(
                        nameById.get(id), required, available, "KG");
            }
        }

        // 6. Deduct ingredient stocks
        for (UUID id : sortedIds) {
            IngredientStock stock = stockById.get(id);
            stock.setQuantityOnHand(stock.getQuantityOnHand()
                    .subtract(requiredByIngredient.get(id)));
            stockRepository.save(stock);
        }

        // 7. Upsert farm store (increments balance under pessimistic lock)
        FarmFeedStore store = getOrCreateStoreForUpdate(farmId);
        BigDecimal newBalance = store.getQuantityOnHandKg().add(producedKg);
        store.setQuantityOnHandKg(newBalance);
        farmStoreRepository.save(store);

        // 8. Bag math — snapshot bagSizeKg
        int bagSizeKg = feedingProperties.getDefaultBagSizeKg();
        BigDecimal bagSize = BigDecimal.valueOf(bagSizeKg);
        int numberOfBags = producedKg.divideToIntegralValue(bagSize).intValue();
        BigDecimal bulkRemainder = producedKg.subtract(
                bagSize.multiply(BigDecimal.valueOf(numberOfBags)));

        // 9. Cost = sum(unitCost × scaledQty). Uses the formulation line's
        //    snapshot unit cost (matches Formulation.totalCost exactly at
        //    scaleFactor = 1.0).
        BigDecimal totalCost = BigDecimal.ZERO;
        for (FormulationLine line : lines) {
            BigDecimal qty = requiredByIngredient.get(line.getIngredientId());
            if (qty != null && line.getUnitCost() != null) {
                totalCost = totalCost.add(line.getUnitCost().multiply(qty));
            }
        }
        totalCost = totalCost.setScale(SCALE_QTY, RoundingMode.HALF_UP);

        // 10. Persist batch
        FeedBatch batch = FeedBatch.builder()
                .farmId(farmId)
                .formulationId(formulation.getId())
                .formulationName(formulation.getName())
                .producedKg(producedKg)
                .bagSizeKg(bagSize)
                .numberOfBags(numberOfBags)
                .bulkRemainderKg(bulkRemainder)
                .costTotal(totalCost)
                .productionDate(req.getProductionDate())
                .notes(req.getNotes())
                .createdByUserId(userId)
                .build();
        FeedBatch saved = feedBatchRepository.save(batch);

        log.info("Produced {} kg from formulation {} (scale={}): batch {} — "
                        + "{} bags × {} kg + {} kg bulk; farm store now {} kg",
                producedKg, formulation.getId(), scaleFactor,
                saved.getId(), numberOfBags, bagSizeKg, bulkRemainder, newBalance);

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FeedBatchResponse> listBatches(UUID farmId, Pageable pageable) {
        return feedBatchRepository.findByFarmId(farmId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FarmStoreResponse getStore(UUID farmId) {
        FarmFeedStore store = farmStoreRepository.findByFarmId(farmId).orElse(null);
        return mapper.toStoreResponse(farmId, store, feedingProperties.getDefaultBagSizeKg());
    }

    /**
     * Locks the farm's store row. If absent, creates it (defaulting balance to 0).
     * <p>
     * Concurrent-insert race: two transactions could both see "no row", both
     * attempt an insert. One wins; the other hits a unique violation on
     * {@code ux_farm_feed_store_farm}. In that case we re-query with
     * {@code PESSIMISTIC_WRITE} — the winner's committed row is now visible
     * and safely lockable.
     */
    private FarmFeedStore getOrCreateStoreForUpdate(UUID farmId) {
        Optional<FarmFeedStore> existing = farmStoreRepository.findByFarmIdForUpdate(farmId);
        if (existing.isPresent()) return existing.get();

        try {
            return farmStoreRepository.saveAndFlush(FarmFeedStore.builder()
                    .farmId(farmId)
                    .quantityOnHandKg(BigDecimal.ZERO)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent farm store creation for {} — retrying with lock", farmId);
            return farmStoreRepository.findByFarmIdForUpdate(farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Farm store could not be acquired after concurrent insert"));
        }
    }
}