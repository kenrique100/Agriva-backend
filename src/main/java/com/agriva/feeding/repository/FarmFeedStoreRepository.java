package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FarmFeedStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying
    @Query(value = """
            INSERT INTO farm_feed_store
                (id, farm_id, quantity_on_hand_kg, version, created_at, updated_at)
            VALUES
                (:id, :farmId, 0, 0, NOW(), NOW())
            ON CONFLICT (farm_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("farmId") UUID farmId);
}