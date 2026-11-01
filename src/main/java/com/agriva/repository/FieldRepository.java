package com.agriva.repository;

import com.agriva.model.entity.Field;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FieldRepository extends JpaRepository<Field, UUID> {

    List<Field> findByFarmId(UUID farmId);
    Optional<Field> findByIdAndFarmId(UUID id, UUID farmId);
    Optional<Field> findByFarmIdAndName(UUID farmId, String name);
    boolean existsByFarmIdAndName(UUID farmId, String name);

    /**
     * Hard-deletes every field for a farm.
     * DB-level ON DELETE CASCADE on crop_cycles (field_id) then cascades
     * to crop_activities and harvests.
     */
    @Modifying
    @Query("DELETE FROM Field f WHERE f.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}