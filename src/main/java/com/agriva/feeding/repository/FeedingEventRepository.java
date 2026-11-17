package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FeedingEvent;
import com.agriva.feeding.port.ConsumerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedingEventRepository extends JpaRepository<FeedingEvent, UUID> {

    @Query("SELECT e FROM FeedingEvent e WHERE e.farmId = :farmId")
    Page<FeedingEvent> findByFarmId(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT e FROM FeedingEvent e " +
            "WHERE e.farmId = :farmId AND e.consumerType = :consumerType")
    Page<FeedingEvent> findByFarmIdAndConsumerType(
            @Param("farmId") UUID farmId,
            @Param("consumerType") ConsumerType consumerType,
            Pageable pageable);

    @Query("SELECT e FROM FeedingEvent e " +
            "WHERE e.farmId = :farmId AND e.consumerType = :consumerType " +
            "AND e.consumerId = :consumerId")
    Page<FeedingEvent> findByFarmIdAndConsumerTypeAndConsumerId(
            @Param("farmId") UUID farmId,
            @Param("consumerType") ConsumerType consumerType,
            @Param("consumerId") UUID consumerId,
            Pageable pageable);

    @Query("SELECT e FROM FeedingEvent e WHERE e.id = :id AND e.farmId = :farmId")
    Optional<FeedingEvent> findByIdAndFarmId(
            @Param("id") UUID id,
            @Param("farmId") UUID farmId);

    @Query("SELECT COALESCE(SUM(e.quantityKg), 0) FROM FeedingEvent e " +
            "WHERE e.farmId = :farmId " +
            "  AND e.consumerType = :consumerType " +
            "  AND e.consumerId = :consumerId " +
            "  AND e.fedAt BETWEEN :from AND :to")
    BigDecimal sumQuantityKgInRange(
            @Param("farmId") UUID farmId,
            @Param("consumerType") ConsumerType consumerType,
            @Param("consumerId") UUID consumerId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    @Query("SELECT e.fedAt, SUM(e.quantityKg), COUNT(e) FROM FeedingEvent e " +
            "WHERE e.farmId = :farmId " +
            "  AND e.consumerType = :consumerType " +
            "  AND e.consumerId = :consumerId " +
            "  AND e.fedAt BETWEEN :from AND :to " +
            "GROUP BY e.fedAt " +
            "ORDER BY e.fedAt ASC")
    java.util.List<Object[]> sumQuantityKgByDay(
            @Param("farmId") UUID farmId,
            @Param("consumerType") ConsumerType consumerType,
            @Param("consumerId") UUID consumerId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);
}