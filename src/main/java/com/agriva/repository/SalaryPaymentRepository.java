package com.agriva.repository;

import com.agriva.model.entity.SalaryPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, UUID> {

    List<SalaryPayment> findByPersonnelId(UUID personnelId);

    boolean existsByPaymentReference(String paymentReference);

    void deleteByPersonnelId(UUID personnelId);

    long countByFarmId(UUID farmId);

    @EntityGraph(attributePaths = {"personnel"})
    Page<SalaryPayment> findByFarmId(UUID farmId, Pageable pageable);

    @EntityGraph(attributePaths = {"personnel"})
    Page<SalaryPayment> findByFarmIdAndPersonnelId(UUID farmId, UUID personnelId, Pageable pageable);

    @EntityGraph(attributePaths = {"personnel"})
    Optional<SalaryPayment> findByIdAndFarmId(UUID id, UUID farmId);
}