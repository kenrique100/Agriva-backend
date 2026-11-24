package com.agriva.feeding.service.impl;

import com.agriva.feeding.kafka.producer.FeedIngredientProducer;
import com.agriva.feeding.mapper.IngredientMapper;
import com.agriva.feeding.model.dto.request.AdjustIngredientStockRequest;
import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.dto.request.WriteOffIngredientStockRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.dto.response.IngredientStatsResponse;
import com.agriva.feeding.model.dto.response.IngredientStockAdjustmentResponse;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import com.agriva.feeding.model.entity.IngredientStockAdjustment;
import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.model.enums.StockAdjustmentType;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockAdjustmentRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.feeding.service.IngredientService;
import com.agriva.feeding.utils.IngredientExcelParser;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
import com.agriva.shared.utils.BatchParsedRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final IngredientStockAdjustmentRepository adjustmentRepo;
    private final StagedFeedingIngredientRepository stagedRepo;
    private final IngredientMapper mapper;
    private final IngredientExcelParser excelParser;
    private final FeedIngredientProducer producer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public IngredientResponse createIngredient(IngredientRequest request, UUID farmId, UUID userId) {
        if (ingredientRepository.existsByFarmIdAndNameLive(farmId, request.getName())) {
            throw new BusinessException("Ingredient with this name already exists");
        }

        Ingredient ingredient = Ingredient.builder()
                .farmId(farmId)
                .name(request.getName().trim())
                .unit(request.getUnit())
                .costPerUnit(request.getCostPerUnit())
                .currency(request.getCurrency())
                .supplier(request.getSupplier())
                .densityKgPerL(request.getDensityKgPerL())
                .crudeProteinPct(request.getCrudeProteinPct())
                .build();

        Ingredient saved = ingredientRepository.save(ingredient);
        log.info("Ingredient {} created for farm {} (name='{}', cp={}, by={})",
                saved.getId(), farmId, saved.getName(),
                saved.getCrudeProteinPct(), userId);

        return mapper.toResponse(saved, null);
    }

    @Override
    @Transactional
    public IngredientResponse createWithInitialStock(IngredientRequest request,
                                                     BigDecimal initialQuantity,
                                                     UUID farmId, UUID userId) {
        Optional<Ingredient> existing =
                ingredientRepository.findByFarmIdAndNameLive(farmId, request.getName());
        UUID ingredientId;

        if (existing.isPresent()) {
            Ingredient ing = existing.get();
            ingredientId = ing.getId();

            if (request.getUnit() != null && ing.getUnit() != request.getUnit()) {
                log.warn("Ingredient '{}' already exists on farm {} with unit {}. "
                                + "Bulk row requested unit {} — unit change is IGNORED here to "
                                + "protect existing stock. Use PUT /ingredients/{} to change unit "
                                + "(requires zero stock).",
                        request.getName(), farmId, ing.getUnit(), request.getUnit(), ing.getId());
            }

            boolean changed = false;
            if (request.getCostPerUnit() != null
                    && !Objects.equals(request.getCostPerUnit(), ing.getCostPerUnit())) {
                ing.setCostPerUnit(request.getCostPerUnit());
                changed = true;
            }
            if (request.getCurrency() != null && request.getCurrency() != ing.getCurrency()) {
                ing.setCurrency(request.getCurrency());
                changed = true;
            }
            if (request.getSupplier() != null
                    && !Objects.equals(request.getSupplier(), ing.getSupplier())) {
                ing.setSupplier(request.getSupplier());
                changed = true;
            }
            if (request.getDensityKgPerL() != null
                    && !Objects.equals(request.getDensityKgPerL(), ing.getDensityKgPerL())) {
                ing.setDensityKgPerL(request.getDensityKgPerL());
                changed = true;
            }
            if (request.getCrudeProteinPct() != null
                    && !Objects.equals(request.getCrudeProteinPct(), ing.getCrudeProteinPct())) {
                ing.setCrudeProteinPct(request.getCrudeProteinPct());
                changed = true;
            }
            if (changed) {
                ingredientRepository.save(ing);
                log.info("Bulk-merge updated ingredient {} ('{}') fields on farm {} (by={})",
                        ing.getId(), ing.getName(), farmId, userId);
            } else {
                log.info("Ingredient '{}' already exists on farm {} — merging stock only",
                        request.getName(), farmId);
            }
        } else {
            IngredientResponse created = createIngredient(request, farmId, userId);
            ingredientId = created.getId();
        }

        if (initialQuantity != null && initialQuantity.signum() > 0) {
            IngredientStockReplenishRequest rep = new IngredientStockReplenishRequest();
            rep.setQuantity(initialQuantity);
            return replenishStock(ingredientId, rep, farmId, userId);
        }

        Ingredient ingredient = ingredientRepository.findByIdAndFarmIdLive(ingredientId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found after creation/merge"));
        IngredientStock stock = stockRepository.findByFarmIdAndIngredientId(farmId, ingredientId).orElse(null);
        return mapper.toResponse(ingredient, stock);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IngredientResponse> listIngredients(UUID farmId, Pageable pageable) {
        Page<Ingredient> page = ingredientRepository.findByFarmIdLive(farmId, pageable);
        if (page.isEmpty()) {
            return page.map(i -> mapper.toResponse(i, null));
        }

        List<UUID> ids = page.map(Ingredient::getId).getContent();
        Map<UUID, IngredientStock> stockById = stockRepository
                .findAllByFarmIdAndIngredientIdIn(farmId, ids)
                .stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, s -> s, (a, b) -> a));

        return page.map(i -> mapper.toResponse(i, stockById.get(i.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public IngredientResponse getIngredient(UUID id, UUID farmId) {
        Ingredient ingredient = loadIngredient(id, farmId);
        IngredientStock stock = stockRepository
                .findByFarmIdAndIngredientId(farmId, id).orElse(null);
        return mapper.toResponse(ingredient, stock);
    }

    @Override
    @Transactional
    public IngredientResponse updateIngredient(UUID id, IngredientRequest request,
                                               UUID farmId, UUID userId) {
        Ingredient ingredient = loadIngredient(id, farmId);

        if (!ingredient.getName().equalsIgnoreCase(request.getName().trim())
                && ingredientRepository.existsByFarmIdAndNameLive(farmId, request.getName())) {
            throw new BusinessException("Ingredient with this name already exists");
        }

        if (!ingredient.getUnit().equals(request.getUnit())) {
            IngredientStock existingStock = stockRepository
                    .findByFarmIdAndIngredientId(farmId, id).orElse(null);
            if (existingStock != null && existingStock.getQuantityOnHand() != null
                    && existingStock.getQuantityOnHand().signum() > 0) {
                throw new BusinessException(
                        "Cannot change unit while stock is non-zero. Zero the stock first.");
            }
        }

        ingredient.setName(request.getName().trim());
        ingredient.setUnit(request.getUnit());
        ingredient.setCostPerUnit(request.getCostPerUnit());
        ingredient.setCurrency(request.getCurrency());
        ingredient.setSupplier(request.getSupplier());
        ingredient.setDensityKgPerL(request.getDensityKgPerL());
        ingredient.setCrudeProteinPct(request.getCrudeProteinPct());

        Ingredient saved = ingredientRepository.save(ingredient);
        IngredientStock stock = stockRepository
                .findByFarmIdAndIngredientId(farmId, id).orElse(null);

        log.info("Ingredient {} updated for farm {} (by={})", id, farmId, userId);
        return mapper.toResponse(saved, stock);
    }

    @Override
    @Transactional
    public IngredientResponse replenishStock(UUID id, IngredientStockReplenishRequest request,
                                             UUID farmId, UUID userId) {
        Ingredient ingredient = loadIngredient(id, farmId);

        if (request.getQuantity() == null
                || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Replenish quantity must be greater than zero");
        }

        IngredientStock stock = stockRepository
                .findByFarmIdAndIngredientIdForUpdate(farmId, id)
                .orElseGet(() -> IngredientStock.builder()
                        .farmId(farmId)
                        .ingredientId(id)
                        .unit(ingredient.getUnit())
                        .quantityOnHand(BigDecimal.ZERO)
                        .build());

        BigDecimal currentQty = stock.getQuantityOnHand() == null
                ? BigDecimal.ZERO : stock.getQuantityOnHand();
        BigDecimal newQty = currentQty.add(request.getQuantity());

        stock.setQuantityOnHand(newQty);
        stock.setLastReplenishedAt(LocalDateTime.now());

        IngredientStock saved = stockRepository.save(stock);
        log.info("Replenished {} {} of ingredient {} for farm {} (new total {}, by={})",
                request.getQuantity(), ingredient.getUnit(), id, farmId, newQty, userId);

        return mapper.toResponse(ingredient, saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id, UUID farmId) {
        Ingredient ingredient = loadIngredient(id, farmId);

        IngredientStock stock = stockRepository.findByFarmIdAndIngredientId(farmId, id).orElse(null);
        if (stock != null && stock.getQuantityOnHand() != null
                && stock.getQuantityOnHand().signum() > 0) {
            throw new BusinessException(
                    "Cannot delete ingredient '" + ingredient.getName() + "' with existing stock ("
                            + stock.getQuantityOnHand() + " " + ingredient.getUnit() + "). "
                            + "Please deplete or write off the stock first.");
        }

        ingredient.setDeletedAt(LocalDateTime.now());
        ingredientRepository.save(ingredient);
        log.info("Soft-deleted ingredient {} from farm {}", id, farmId);
    }

    @Override
    @Transactional
    public IngredientResponse adjustStock(UUID id, AdjustIngredientStockRequest request,
                                          UUID farmId, UUID userId) {
        Ingredient ingredient = loadIngredient(id, farmId);

        IngredientStock stock = stockRepository
                .findByFarmIdAndIngredientIdForUpdate(farmId, id)
                .orElseGet(() -> IngredientStock.builder()
                        .farmId(farmId)
                        .ingredientId(id)
                        .unit(ingredient.getUnit())
                        .quantityOnHand(BigDecimal.ZERO)
                        .build());

        assertVersionMatches(stock.getVersion(), request.getExpectedVersion(), "stock");

        BigDecimal previous = stock.getQuantityOnHand() == null
                ? BigDecimal.ZERO : stock.getQuantityOnHand();
        BigDecimal target = request.getQuantityOnHand();

        stock.setQuantityOnHand(target);
        stock.setLastReplenishedAt(LocalDateTime.now());
        IngredientStock saved = stockRepository.save(stock);

        recordAdjustment(farmId, ingredient, previous, target,
                StockAdjustmentType.ABSOLUTE_SET, request.getReason(), userId);

        log.info("Ingredient {} stock SET {} -> {} {} on farm {} (reason='{}', by={})",
                id, previous, target, ingredient.getUnit(),
                farmId, request.getReason(), userId);

        return mapper.toResponse(ingredient, saved);
    }

    @Override
    @Transactional
    public IngredientResponse writeOffStock(UUID id, WriteOffIngredientStockRequest request,
                                            UUID farmId, UUID userId) {
        Ingredient ingredient = loadIngredient(id, farmId);

        IngredientStock stock = stockRepository
                .findByFarmIdAndIngredientIdForUpdate(farmId, id)
                .orElseThrow(() -> new BusinessException(
                        "Cannot write off: ingredient '" + ingredient.getName()
                                + "' has no stock record on this farm"));

        assertVersionMatches(stock.getVersion(), request.getExpectedVersion(), "stock");

        BigDecimal previous = stock.getQuantityOnHand() == null
                ? BigDecimal.ZERO : stock.getQuantityOnHand();
        if (previous.compareTo(request.getQuantity()) < 0) {
            throw new BusinessException(
                    "Cannot write off " + request.getQuantity() + " " + ingredient.getUnit()
                            + " — only " + previous + " " + ingredient.getUnit()
                            + " on hand for ingredient '" + ingredient.getName() + "'");
        }
        BigDecimal target = previous.subtract(request.getQuantity());

        stock.setQuantityOnHand(target);
        IngredientStock saved = stockRepository.save(stock);

        recordAdjustment(farmId, ingredient, previous, target,
                StockAdjustmentType.WRITEOFF, request.getReason(), userId);

        log.info("Ingredient {} stock WRITE-OFF {} -> {} {} on farm {} (reason='{}', by={})",
                id, previous, target, ingredient.getUnit(),
                farmId, request.getReason(), userId);

        return mapper.toResponse(ingredient, saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IngredientStockAdjustmentResponse> listStockAdjustments(
            UUID id, UUID farmId, Pageable pageable) {
        loadIngredient(id, farmId);
        return adjustmentRepo
                .findByFarmIdAndIngredientIdOrderByPerformedAtDesc(farmId, id, pageable)
                .map(this::toAdjustmentResponse);
    }

    @Override
    public BatchUploadResponse bulkUpload(MultipartFile file, UUID farmId,
                                          UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        List<BatchParsedRow<IngredientSubmissionPayload>> rows;
        try {
            rows = excelParser.parse(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to read Excel file: " + e.getMessage(), e);
        }

        UUID batchId = UUID.randomUUID();
        BatchUploadResponse response = new BatchUploadResponse();
        response.setBatchId(batchId);
        response.setTotalRows(rows.size());

        for (BatchParsedRow<IngredientSubmissionPayload> row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new BatchUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }

            UUID submissionId = UUID.randomUUID();
            try {
                String payloadJson = serialize(row.request());
                producer.publish(new FeedIngredientSubmissionEvent(
                        submissionId, farmId, userId, email,
                        payloadJson, LocalDateTime.now(),
                        batchId, row.rowNumber()));

                response.setQueued(response.getQueued() + 1);
                response.getResults().add(new BatchUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new BatchUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
                log.warn("Bulk ingredient upload {} row {} failed: {}",
                        batchId, row.rowNumber(), e.getMessage());
            }
        }

        log.info("Bulk ingredient upload {} for farm {}: total={}, queued={}, failed={}",
                batchId, farmId, response.getTotalRows(),
                response.getQueued(), response.getFailed());

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BatchStatusResponse bulkStatus(UUID batchId, UUID farmId) {
        List<StagedFeedingIngredient> staged =
                stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId);

        if (staged.isEmpty()) {
            throw new ResourceNotFoundException("Batch not found: " + batchId);
        }

        int pending = 0, moved = 0, failed = 0;
        List<BatchStatusResponse.FailedRow> failedRows = new ArrayList<>();

        for (StagedFeedingIngredient s : staged) {
            switch (s.getStatus()) {
                case PENDING, RETRYING -> pending++;
                case MOVED -> moved++;
                case FAILED -> {
                    failed++;
                    failedRows.add(new BatchStatusResponse.FailedRow(
                            s.getRowNumber(), s.getId(), s.getValidationErrors()));
                }
            }
        }

        return BatchStatusResponse.builder()
                .batchId(batchId)
                .total(staged.size())
                .pending(pending)
                .moved(moved)
                .failed(failed)
                .failedRows(failedRows.isEmpty() ? null : failedRows)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public IngredientStatsResponse getStats(UUID farmId) {
        long totalIngredients = ingredientRepository.countLiveByFarmId(farmId);
        BigDecimal totalStockKg = stockRepository
                .sumQuantityByFarmIdAndUnit(farmId, IngredientUnit.KG);
        BigDecimal currentStockValue = stockRepository.sumStockValueByFarmId(farmId);

        String currency = ingredientRepository.findDistinctCurrenciesByFarmIdLive(farmId)
                .stream()
                .findFirst()
                .map(Enum::name)
                .orElse(null);

        return IngredientStatsResponse.builder()
                .totalIngredients(totalIngredients)
                .totalStockKg(round(totalStockKg))
                .currentStockValue(round(currentStockValue))
                .currency(currency)
                .build();
    }

    private static void assertVersionMatches(Long actual, Long expected, String label) {
        if (expected != null && !expected.equals(actual)) {
            throw new BusinessException(
                    "The " + label + " was modified by someone else (expected version "
                            + expected + ", current " + actual + "). Reload and try again.");
        }
    }

    private void recordAdjustment(UUID farmId, Ingredient ingredient,
                                  BigDecimal previous, BigDecimal target,
                                  StockAdjustmentType type, String reason, UUID userId) {
        adjustmentRepo.save(IngredientStockAdjustment.builder()
                .farmId(farmId)
                .ingredientId(ingredient.getId())
                .previousQuantity(previous)
                .newQuantity(target)
                .delta(target.subtract(previous))
                .unit(ingredient.getUnit())
                .reason(reason)
                .adjustmentType(type)
                .performedByUserId(userId)
                .build());
    }

    private IngredientStockAdjustmentResponse toAdjustmentResponse(IngredientStockAdjustment a) {
        return IngredientStockAdjustmentResponse.builder()
                .id(a.getId())
                .farmId(a.getFarmId())
                .ingredientId(a.getIngredientId())
                .previousQuantity(a.getPreviousQuantity())
                .newQuantity(a.getNewQuantity())
                .delta(a.getDelta())
                .unit(a.getUnit())
                .reason(a.getReason())
                .adjustmentType(a.getAdjustmentType())
                .performedByUserId(a.getPerformedByUserId())
                .performedAt(a.getPerformedAt())
                .build();
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(4, RoundingMode.HALF_UP);
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to serialize ingredient payload", e);
        }
    }

    private Ingredient loadIngredient(UUID id, UUID farmId) {
        return ingredientRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ingredient not found with ID: " + id));
    }
}