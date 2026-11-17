package com.agriva.feeding.service.impl;

import com.agriva.feeding.mapper.IngredientMapper;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.service.IngredientService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final IngredientMapper mapper;

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
                .build();

        Ingredient saved = ingredientRepository.save(ingredient);
        log.info("Ingredient {} created for farm {} (name='{}', by={})",
                saved.getId(), farmId, saved.getName(), userId);
        return mapper.toResponse(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IngredientResponse> listIngredients(UUID farmId, Pageable pageable) {
        Page<Ingredient> page = ingredientRepository.findByFarmIdLive(farmId, pageable);
        if (page.isEmpty()) {
            return page.map(i -> mapper.toResponse(i, null));
        }

        // Batch-fetch all stocks in ONE query rather than one-per-row.
        List<UUID> ids = page.map(Ingredient::getId).getContent();
        Map<UUID, IngredientStock> stockById = stockRepository.findAllByFarmId(farmId)
                .stream()
                .filter(s -> ids.contains(s.getIngredientId()))
                .collect(Collectors.toMap(
                        IngredientStock::getIngredientId,
                        s -> s,
                        (a, b) -> a));

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

        BigDecimal newQty = stock.getQuantityOnHand().add(request.getQuantity());
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
        stockRepository.findByFarmIdAndIngredientId(farmId, id).ifPresent(stock -> {
            if (stock.getQuantityOnHand() != null
                    && stock.getQuantityOnHand().signum() != 0) {
                stock.setQuantityOnHand(BigDecimal.ZERO);
                stockRepository.save(stock);
            }
        });

        ingredient.setDeletedAt(LocalDateTime.now());
        ingredientRepository.save(ingredient);
        log.info("Soft-deleted ingredient {} from farm {}", id, farmId);
    }

    private Ingredient loadIngredient(UUID id, UUID farmId) {
        return ingredientRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ingredient not found with ID: " + id));
    }
}