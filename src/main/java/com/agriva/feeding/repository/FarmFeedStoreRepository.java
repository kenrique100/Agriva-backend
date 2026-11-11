package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FarmFeedStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FarmFeedStoreRepository extends JpaRepository<FarmFeedStore, UUID> {

    Optional<FarmFeedStore> findByFarmId(UUID farmId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM FarmFeedStore s WHERE s.farmId = :farmId")
    Optional<FarmFeedStore> findByFarmIdForUpdate(@Param("farmId") UUID farmId);
}