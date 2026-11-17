package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.IngredientStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientStockRepository extends JpaRepository<IngredientStock, UUID> {

    Optional<IngredientStock> findByFarmIdAndIngredientId(UUID farmId, UUID ingredientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM IngredientStock s WHERE s.farmId = :farmId AND s.ingredientId = :ingredientId")
    Optional<IngredientStock> findByFarmIdAndIngredientIdForUpdate(
            @Param("farmId") UUID farmId, @Param("ingredientId") UUID ingredientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM IngredientStock s WHERE s.farmId = :farmId AND s.ingredientId IN :ids")
    List<IngredientStock> findAllForUpdate(
            @Param("farmId") UUID farmId, @Param("ids") List<UUID> ingredientIds);

    List<IngredientStock> findAllByFarmId(UUID farmId);
}