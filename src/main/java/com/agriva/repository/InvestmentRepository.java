package com.agriva.repository;

import com.agriva.model.entity.Investment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    Page<Investment> findByFarmId(UUID farmId, Pageable pageable);

    Optional<Investment> findByIdAndFarmId(UUID id, UUID farmId);

    List<Investment> findByTransactionId(UUID transactionId);

    long countByFarmId(UUID farmId);

    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    @Query("SELECT SUM(i.amount) FROM Investment i WHERE i.farmId = :farmId")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(i.amount) FROM Investment i WHERE i.farmId = :farmId AND i.createdByUserId = :userId")
    BigDecimal sumAmountByFarmIdAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                   @Param("userId") UUID userId);

    default BigDecimal sumAmountByFarmIdOrZero(UUID farmId) {
        BigDecimal v = sumAmountByFarmId(farmId);
        return v == null ? BigDecimal.ZERO : v;
    }

    default BigDecimal sumAmountByFarmIdAndUserOrZero(UUID farmId, UUID userId) {
        BigDecimal v = sumAmountByFarmIdAndCreatedByUserId(farmId, userId);
        return v == null ? BigDecimal.ZERO : v;
    }
}