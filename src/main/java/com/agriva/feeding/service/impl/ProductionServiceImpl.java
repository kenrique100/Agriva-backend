package com.agriva.feeding.service.impl;

import com.agriva.feeding.config.FeedingProperties;
import com.agriva.feeding.mapper.FeedBatchMapper;
import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.response.FarmStoreResponse;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.entity.*;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FarmStoreService;
import com.agriva.feeding.service.ProductionService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final FeedBatchRepository feedBatchRepository;
    private final DemandForecastRepository demandForecastRepository;
    private final FarmStoreService farmStoreService;
    private final FeedBatchMapper mapper;
    private final FeedingProperties feedingProperties;

    @Override
    @Transactional
    public FeedBatchResponse produce(ProductionRequest req, UUID farmId, UUID userId) {
        if (req.getProducedKg() == null || req.getProducedKg().signum() <= 0) {
            throw new BusinessException("producedKg must be positive");
        }
        if (req.getProductionDate() == null || req.getProductionDate().isAfter(LocalDate.now())) {
            throw new BusinessException("productionDate cannot be in the future");
        }

        if (req.getForecastId() != null) {
            demandForecastRepository.findByIdAndFarmIdLive(req.getForecastId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Forecast not found or not accessible on this farm: "
                                    + req.getForecastId()));
        }

        Formulation formulation = formulationRepository
                .findByIdAndFarmIdLive(req.getFormulationId(), farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + req.getFormulationId()));

        List<FormulationLine> lines = formulationLineRepository
                .findByFormulationIdOrderByPercentageDesc(formulation.getId());
        if (lines.isEmpty()) {
            throw new BusinessException("Formulation has no lines; cannot produce");
        }

        BigDecimal baseTotal = formulation.getTotalKg();
        if (baseTotal == null || baseTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Formulation totalKg must be positive; cannot compute production scale");
        }
        BigDecimal producedKg = req.getProducedKg();
        BigDecimal scaleFactor = producedKg.divide(baseTotal, SCALE_CALC, RoundingMode.HALF_UP);

        Map<UUID, BigDecimal> requiredByIngredient = new LinkedHashMap<>();
        Map<UUID, String> nameById = new HashMap<>();
        for (FormulationLine line : lines) {
            BigDecimal required = line.getQuantityKg()
                    .multiply(scaleFactor)
                    .setScale(SCALE_QTY, RoundingMode.HALF_UP);
            requiredByIngredient.merge(line.getIngredientId(), required, BigDecimal::add);
            nameById.put(line.getIngredientId(), line.getIngredientName());
        }

        List<UUID> allIngredientIds = requiredByIngredient.keySet().stream().toList();
        Map<UUID, String> unitById = ingredientRepository
                .findAllById(allIngredientIds).stream()
                .collect(Collectors.toMap(
                        Ingredient::getId,
                        i -> i.getUnit() == null ? "KG" : i.getUnit().name(),
                        (a, b) -> a));

        List<UUID> sortedIds = requiredByIngredient.keySet().stream().sorted().toList();
        List<IngredientStock> lockedStocks = stockRepository.findAllForUpdate(farmId, sortedIds);
        Map<UUID, IngredientStock> stockById = lockedStocks.stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, s -> s));

        for (UUID id : sortedIds) {
            BigDecimal required = requiredByIngredient.get(id);
            IngredientStock stock = stockById.get(id);
            BigDecimal available = stock == null || stock.getQuantityOnHand() == null
                    ? BigDecimal.ZERO : stock.getQuantityOnHand();
            if (available.compareTo(required) < 0) {
                String unit = unitById.getOrDefault(id, "KG");
                throw new InsufficientIngredientQuantityException(
                        nameById.get(id), required, available, unit);
            }
        }

        for (UUID id : sortedIds) {
            IngredientStock stock = stockById.get(id);
            stock.setQuantityOnHand(stock.getQuantityOnHand().subtract(requiredByIngredient.get(id)));
            stockRepository.save(stock);
        }

        FarmFeedStore store = farmStoreService.getOrCreateForUpdate(farmId);
        BigDecimal newBalance = store.getQuantityOnHandKg().add(producedKg);
        store.setQuantityOnHandKg(newBalance);
        farmStoreService.save(store);

        int bagSizeKg = feedingProperties.getDefaultBagSizeKg();
        BigDecimal bagSize = BigDecimal.valueOf(bagSizeKg);
        long numberOfBagsLong = producedKg.divideToIntegralValue(bagSize).longValue();
        if (numberOfBagsLong > Integer.MAX_VALUE) {
            throw new BusinessException("Produced quantity exceeds maximum bag count limit");
        }
        int numberOfBags = (int) numberOfBagsLong;
        BigDecimal bulkRemainder = producedKg.subtract(
                bagSize.multiply(BigDecimal.valueOf(numberOfBags)));

        BigDecimal totalCost = BigDecimal.ZERO;
        for (FormulationLine line : lines) {
            BigDecimal qty = requiredByIngredient.get(line.getIngredientId());
            if (qty != null && line.getUnitCost() != null) {
                totalCost = totalCost.add(line.getUnitCost().multiply(qty));
            }
        }
        totalCost = totalCost.setScale(SCALE_QTY, RoundingMode.HALF_UP);

        FeedBatch batch = FeedBatch.builder()
                .farmId(farmId)
                .formulationId(formulation.getId())
                .forecastId(req.getForecastId())
                .formulationName(formulation.getName())
                .producedKg(producedKg)
                .bagSizeKg(bagSize)
                .numberOfBags(numberOfBags)
                .bulkRemainderKg(bulkRemainder)
                .costTotal(totalCost)
                .productionDate(req.getProductionDate())
                .notes(req.getNotes())
                .createdByUserId(userId)
                .idempotencyKey(req.getIdempotencyKey())
                .build();

        // saveAndFlush when idempotencyKey is set — the unique constraint must
        // fire inside this tx so a race rolls back the stock decrements too.
        FeedBatch saved = (req.getIdempotencyKey() != null)
                ? feedBatchRepository.saveAndFlush(batch)
                : feedBatchRepository.save(batch);

        log.info("Produced {} kg from formulation {} (forecast={}): batch {} — farm store now {} kg",
                producedKg, formulation.getId(), req.getForecastId(), saved.getId(), newBalance);

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
        FarmFeedStore store = farmStoreService.get(farmId);
        return mapper.toStoreResponse(farmId, store, feedingProperties.getDefaultBagSizeKg());
    }
}