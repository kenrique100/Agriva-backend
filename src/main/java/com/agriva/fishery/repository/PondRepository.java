package com.agriva.fishery.repository;

import com.agriva.fishery.model.entity.Pond;
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
public interface PondRepository extends JpaRepository<Pond, UUID> {

    @Query("SELECT p FROM Pond p WHERE p.farmId = :farmId AND p.deletedAt IS NULL")
    Page<Pond> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT p FROM Pond p WHERE p.id = :id AND p.farmId = :farmId AND p.deletedAt IS NULL")
    Optional<Pond> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT p FROM Pond p WHERE p.farmId = :farmId " +
            "AND LOWER(p.name) = LOWER(:name) AND p.deletedAt IS NULL")
    Optional<Pond> findByFarmIdAndNameLive(@Param("farmId") UUID farmId,
                                           @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM Pond p " +
            "WHERE p.farmId = :farmId AND LOWER(p.name) = LOWER(:name) AND p.deletedAt IS NULL")
    boolean existsByFarmIdAndNameLive(@Param("farmId") UUID farmId,
                                      @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM Pond p " +
            "WHERE p.farmId = :farmId AND LOWER(p.name) = LOWER(:name) " +
            "AND p.id <> :excludeId AND p.deletedAt IS NULL")
    boolean existsByFarmIdAndNameLiveExcluding(@Param("farmId") UUID farmId,
                                               @Param("name") String name,
                                               @Param("excludeId") UUID excludeId);

    /**
     * Soft-deletes every pond for a farm.
     * NOTE (DR-1 limitation): {@code AuthServiceImpl.deleteFarmAndAllUsersInternal}
     * does not yet call this. Ponds for a soft-deleted farm therefore remain
     * queryable until the JWT expires. Fix scheduled for DR-2.
     */
    @Modifying
    @Query("UPDATE Pond p SET p.deletedAt = :now " +
            "WHERE p.farmId = :farmId AND p.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId, @Param("now") LocalDateTime now);
}