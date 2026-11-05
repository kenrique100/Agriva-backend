package com.agriva.farming.repository;
import com.agriva.farming.model.entity.Crop;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface CropRepository extends JpaRepository<Crop, UUID> {
    List<Crop> findByFarmId(UUID farmId);
    Optional<Crop> findByIdAndFarmId(UUID id, UUID farmId);
    Optional<Crop> findByFarmIdAndName(UUID farmId, String name);
    Optional<Crop> findByFarmIdAndNameAndVariety(UUID farmId, String name, String variety);

    @Modifying
    @Query("DELETE FROM Crop c WHERE c.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}