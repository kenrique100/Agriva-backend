package com.agriva.repository;

import com.agriva.model.entity.Crop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CropRepository extends JpaRepository<Crop, UUID> {

    List<Crop> findByFarmId(UUID farmId);
    Optional<Crop> findByIdAndFarmId(UUID id, UUID farmId);
    Optional<Crop> findByFarmIdAndName(UUID farmId, String name);
    Optional<Crop> findByFarmIdAndNameAndVariety(UUID farmId, String name, String variety);

    /** Hard-deletes every crop for a farm; DB cascade removes dependent crop_cycles. */
    @Modifying
    @Query("DELETE FROM Crop c WHERE c.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}