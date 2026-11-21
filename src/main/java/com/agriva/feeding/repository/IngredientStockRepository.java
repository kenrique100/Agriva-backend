package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.IngredientStock;
import com.agriva.feeding.model.enums.IngredientUnit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Query("SELECT COALESCE(SUM(s.quantityOnHand), 0) FROM IngredientStock s " +
            "WHERE s.farmId = :farmId AND s.unit = :unit")
    BigDecimal sumQuantityByFarmIdAndUnit(@Param("farmId") UUID farmId,
                                          @Param("unit") IngredientUnit unit);

    @Query(value = """
        SELECT COALESCE(SUM(s.quantity_on_hand * i.cost_per_unit), 0)
        FROM ingredient_stock s
        JOIN ingredient i ON i.id = s.ingredient_id
        WHERE s.farm_id = :farmId
          AND i.deleted_at IS NULL
          AND i.cost_per_unit IS NOT NULL
        """, nativeQuery = true)
    BigDecimal sumStockValueByFarmId(@Param("farmId") UUID farmId);
}