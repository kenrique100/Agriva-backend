package com.agriva.poultry.repository;

import com.agriva.poultry.model.entity.PoultryHouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoultryHouseRepository extends JpaRepository<PoultryHouse, UUID> {

    @Query("SELECT h FROM PoultryHouse h WHERE h.farmId = :farmId AND h.deletedAt IS NULL")
    Page<PoultryHouse> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT h FROM PoultryHouse h WHERE h.id = :id AND h.farmId = :farmId " +
            "AND h.deletedAt IS NULL")
    Optional<PoultryHouse> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT h FROM PoultryHouse h WHERE h.farmId = :farmId " +
            "AND LOWER(h.name) = LOWER(:name) AND h.deletedAt IS NULL")
    Optional<PoultryHouse> findByFarmIdAndNameLive(@Param("farmId") UUID farmId,
                                                   @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END FROM PoultryHouse h " +
            "WHERE h.farmId = :farmId AND LOWER(h.name) = LOWER(:name) AND h.deletedAt IS NULL")
    boolean existsByFarmIdAndNameLive(@Param("farmId") UUID farmId, @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END FROM PoultryHouse h " +
            "WHERE h.farmId = :farmId AND LOWER(h.name) = LOWER(:name) " +
            "AND h.id <> :excludeId AND h.deletedAt IS NULL")
    boolean existsByFarmIdAndNameLiveExcluding(@Param("farmId") UUID farmId,
                                               @Param("name") String name,
                                               @Param("excludeId") UUID excludeId);

    @Modifying
    @Query("UPDATE PoultryHouse h SET h.deletedAt = :now " +
            "WHERE h.farmId = :farmId AND h.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId, @Param("now") LocalDateTime now);
}