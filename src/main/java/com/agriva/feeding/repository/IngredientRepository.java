package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.Ingredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    @Query("SELECT i FROM Ingredient i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    Page<Ingredient> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT i FROM Ingredient i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    List<Ingredient> findAllByFarmIdLive(@Param("farmId") UUID farmId);

    @Query("SELECT i FROM Ingredient i WHERE i.id = :id AND i.farmId = :farmId AND i.deletedAt IS NULL")
    Optional<Ingredient> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT i FROM Ingredient i WHERE i.farmId = :farmId " +
            "AND LOWER(i.name) = LOWER(:name) AND i.deletedAt IS NULL")
    Optional<Ingredient> findByFarmIdAndNameLive(@Param("farmId") UUID farmId,
                                                 @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END FROM Ingredient i " +
            "WHERE i.farmId = :farmId AND LOWER(i.name) = LOWER(:name) AND i.deletedAt IS NULL")
    boolean existsByFarmIdAndNameLive(@Param("farmId") UUID farmId, @Param("name") String name);

    @Query("SELECT COUNT(i) FROM Ingredient i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    long countLiveByFarmId(@Param("farmId") UUID farmId);

    @Modifying
    @Query("UPDATE Ingredient i SET i.deletedAt = :now " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId, @Param("now") LocalDateTime now);
}