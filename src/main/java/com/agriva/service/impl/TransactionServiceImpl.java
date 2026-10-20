package com.agriva.service.impl;

import com.agriva.exception.BusinessException;
import com.agriva.exception.TransactionNotFoundException;
import com.agriva.mapper.TransactionMapper;
import com.agriva.model.dto.transaction.TransactionRequest;
import com.agriva.model.dto.transaction.TransactionResponse;
import com.agriva.model.dto.transaction.TransactionSummaryResponse;
import com.agriva.model.entity.Expense;
import com.agriva.model.entity.Income;
import com.agriva.model.entity.Investment;
import com.agriva.model.entity.Transaction;
import com.agriva.model.entity.TransactionStatus;
import com.agriva.model.entity.User;
import com.agriva.repository.*;
import com.agriva.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository txRepo;
    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final InvestmentRepository investmentRepo;
    private final AuthRepository authRepository;
    private final TransactionMapper mapper;

    @Override
    @Transactional
    public TransactionResponse create(TransactionRequest request, UUID farmId, UUID userId) {
        String ref = request.getTransactionRef();
        if (ref == null || ref.isBlank()) {
            ref = generateRef(farmId);
        } else if (txRepo.existsByFarmIdAndTransactionRef(farmId, ref)) {
            throw new BusinessException("Transaction ref already exists on this farm: " + ref);
        }

        Transaction tx = txRepo.save(Transaction.builder()
                .farmId(farmId)
                .transactionRef(ref)
                .occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now())
                .status(TransactionStatus.DRAFT)
                .notes(request.getNotes())
                .createdByUserId(userId)
                .build());

        log.info("Transaction {} created for farm {}", tx.getTransactionRef(), farmId);
        return mapper.toResponse(tx, resolveUsername(tx.getCreatedByUserId()));
    }

    @Override
    public Page<TransactionResponse> list(UUID farmId, Pageable pageable) {
        return txRepo.findByFarmId(farmId, pageable)
                .map(tx -> mapper.toResponse(tx, resolveUsername(tx.getCreatedByUserId())));
    }

    @Override
    public TransactionResponse get(UUID id, UUID farmId) {
        Transaction tx = load(id, farmId);
        return mapper.toResponse(tx, resolveUsername(tx.getCreatedByUserId()));
    }

    @Override
    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request, UUID farmId) {
        Transaction tx = load(id, farmId);

        if (request.getTransactionRef() != null
                && !request.getTransactionRef().equals(tx.getTransactionRef())) {
            if (txRepo.existsByFarmIdAndTransactionRef(farmId, request.getTransactionRef())) {
                throw new BusinessException("Transaction ref already exists on this farm: "
                        + request.getTransactionRef());
            }
            tx.setTransactionRef(request.getTransactionRef());
        }
        if (request.getOccurredAt() != null) tx.setOccurredAt(request.getOccurredAt());
        if (request.getNotes() != null) tx.setNotes(request.getNotes());

        Transaction saved = txRepo.save(tx);
        return mapper.toResponse(saved, resolveUsername(saved.getCreatedByUserId()));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID farmId) {
        Transaction tx = load(id, farmId);
        txRepo.delete(tx);
        log.info("Transaction {} deleted from farm {}", tx.getTransactionRef(), farmId);
    }

    @Override
    public TransactionSummaryResponse summary(UUID id, UUID farmId) {
        Transaction tx = load(id, farmId);

        BigDecimal income = sumIncome(tx.getId());
        BigDecimal expense = sumExpense(tx.getId());
        BigDecimal investment = sumInvestment(tx.getId());

        BigDecimal netPosition = income.subtract(expense).subtract(investment);
        BigDecimal netGain = netPosition.signum() > 0 ? netPosition : BigDecimal.ZERO;
        BigDecimal netLoss = netPosition.signum() < 0 ? netPosition.abs() : BigDecimal.ZERO;

        return new TransactionSummaryResponse(
                tx.getId(),
                tx.getTransactionRef(),
                tx.getStatus().name(),
                income,
                expense,
                investment,
                netGain,
                netLoss,
                netPosition
        );
    }

    @Override
    @Transactional
    public int backfillFromIncome(UUID farmId, UUID userId) {
        List<Income> orphans = incomeRepo.findByFarmIdAndTransactionIdIsNull(farmId);
        if (orphans.isEmpty()) {
            log.info("backfill: no unlinked income rows for farm {}", farmId);
            return 0;
        }

        var byDate = orphans.stream().collect(java.util.stream.Collectors.groupingBy(Income::getIncomeDate));

        int created = 0;
        for (var entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<Income> group = entry.getValue();

            String ref = "TXN-BF-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + String.format("%04d", (int) (Math.random() * 10000));

            Transaction tx = txRepo.save(Transaction.builder()
                    .farmId(farmId)
                    .transactionRef(ref)
                    .occurredAt(date.atStartOfDay())
                    .status(TransactionStatus.DRAFT)
                    .notes("Backfilled from existing income on " + date)
                    .createdByUserId(userId)
                    .build());

            group.forEach(inc -> inc.setTransactionId(tx.getId()));
            incomeRepo.saveAll(group);
            created++;
        }
        return created;
    }

    private Transaction load(UUID id, UUID farmId) {
        return txRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with ID: " + id));
    }

    private BigDecimal sumIncome(UUID txId) {
        return incomeRepo.findByTransactionId(txId).stream()
                .map(Income::getAmountReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumExpense(UUID txId) {
        return expenseRepo.findByTransactionId(txId).stream()
                .map(Expense::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumInvestment(UUID txId) {
        return investmentRepo.findByTransactionId(txId).stream()
                .map(Investment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generateRef(UUID farmId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String candidate;
        int attempt = 0;
        do {
            String suffix = String.format("%04d", (int) (Math.random() * 10000));
            candidate = "TXN-" + date + "-" + suffix;
            attempt++;
        } while (txRepo.existsByFarmIdAndTransactionRef(farmId, candidate) && attempt < 10);
        return candidate;
    }

    private String resolveUsername(UUID userId) {
        if (userId == null) return null;
        return authRepository.findById(userId)
                .map(User::getUsername)
                .orElse("deleted-user");
    }
}