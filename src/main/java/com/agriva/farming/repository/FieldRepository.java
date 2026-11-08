package com.agriva.farming.repository;
import com.agriva.farming.model.entity.Field;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface FieldRepository extends JpaRepository<Field, UUID> {
    List<Field> findByFarmId(UUID farmId);
    Optional<Field> findByIdAndFarmId(UUID id, UUID farmId);
    Optional<Field> findByFarmIdAndName(UUID farmId, String name);
    boolean existsByFarmIdAndName(UUID farmId, String name);

    @Modifying
    @Query("DELETE FROM Field f WHERE f.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}