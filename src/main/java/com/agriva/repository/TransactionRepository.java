package com.agriva.repository;

import com.agriva.model.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByFarmId(UUID farmId, Pageable pageable);

    Optional<Transaction> findByIdAndFarmId(UUID id, UUID farmId);

    boolean existsByFarmIdAndTransactionRef(UUID farmId, String transactionRef);

    long countByFarmId(UUID farmId);
}