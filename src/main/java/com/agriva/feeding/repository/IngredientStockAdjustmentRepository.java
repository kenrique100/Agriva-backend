package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.IngredientStockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IngredientStockAdjustmentRepository
        extends JpaRepository<IngredientStockAdjustment, UUID> {

    Page<IngredientStockAdjustment> findByFarmIdAndIngredientIdOrderByPerformedAtDesc(
            UUID farmId, UUID ingredientId, Pageable pageable);

    Page<IngredientStockAdjustment> findByFarmIdOrderByPerformedAtDesc(
            UUID farmId, Pageable pageable);
}