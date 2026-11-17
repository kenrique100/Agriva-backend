package com.agriva.poultry.repository;

import com.agriva.poultry.model.entity.PoultryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoultryBatchRepository extends JpaRepository<PoultryBatch, UUID> {

    @Query("SELECT b FROM PoultryBatch b WHERE b.houseId = :houseId " +
            "ORDER BY b.arrivalDate DESC, b.createdAt DESC")
    List<PoultryBatch> findByHouseId(@Param("houseId") UUID houseId);

    @Query("SELECT b FROM PoultryBatch b WHERE b.houseId = :houseId " +
            "AND b.status = com.agriva.poultry.model.enums.PoultryBatchStatus.ACTIVE " +
            "ORDER BY b.arrivalDate DESC, b.createdAt DESC")
    List<PoultryBatch> findActiveByHouseId(@Param("houseId") UUID houseId);

    @Query("SELECT b FROM PoultryBatch b WHERE b.farmId = :farmId " +
            "ORDER BY b.arrivalDate DESC, b.createdAt DESC")
    Page<PoultryBatch> findByFarmId(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT b FROM PoultryBatch b WHERE b.id = :id AND b.farmId = :farmId")
    Optional<PoultryBatch> findByIdAndFarmId(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PoultryBatch b WHERE b.id = :id AND b.farmId = :farmId")
    Optional<PoultryBatch> findByIdAndFarmIdForUpdate(
            @Param("id") UUID id, @Param("farmId") UUID farmId);
}