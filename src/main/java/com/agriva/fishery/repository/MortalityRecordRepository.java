package com.agriva.fishery.repository;

import com.agriva.fishery.model.entity.MortalityRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MortalityRecordRepository extends JpaRepository<MortalityRecord, UUID> {

    @Query("SELECT m FROM MortalityRecord m WHERE m.pondId = :pondId")
    Page<MortalityRecord> findByPondId(@Param("pondId") UUID pondId, Pageable pageable);

    @Query("SELECT m FROM MortalityRecord m WHERE m.farmId = :farmId")
    Page<MortalityRecord> findByFarmId(@Param("farmId") UUID farmId, Pageable pageable);
}