package com.agriva.finance.service.impl;

import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.TransactionNotFoundException;
import com.agriva.finance.mapper.TransactionMapper;
import com.agriva.finance.model.dto.request.TransactionRequest;
import com.agriva.finance.model.dto.response.TransactionResponse;
import com.agriva.finance.model.dto.response.TransactionSummaryResponse;
import com.agriva.finance.model.entity.Expense;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.entity.Investment;
import com.agriva.finance.model.entity.Transaction;
import com.agriva.identity.models.entity.User;
import com.agriva.finance.model.enums.TransactionStatus;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.TransactionRepository;
import com.agriva.finance.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
        } else if (txRepo.existsByFarmIdAndTransactionRefLive(farmId, ref)) {
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
        return enrichResponse(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(UUID farmId, Pageable pageable) {
        return txRepo.findByFarmIdLive(farmId, pageable).map(this::enrichResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id, UUID farmId) {
        return enrichResponse(load(id, farmId));
    }

    @Override
    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request, UUID farmId) {
        Transaction tx = load(id, farmId);

        if (request.getTransactionRef() != null && !request.getTransactionRef().equals(tx.getTransactionRef())) {
            if (txRepo.existsByFarmIdAndTransactionRefLive(farmId, request.getTransactionRef())) {
                throw new BusinessException("Transaction ref already exists on this farm: " + request.getTransactionRef());
            }
            tx.setTransactionRef(request.getTransactionRef());
        }
        if (request.getOccurredAt() != null) tx.setOccurredAt(request.getOccurredAt());
        if (request.getNotes() != null) tx.setNotes(request.getNotes());

        Transaction saved = txRepo.save(tx);
        return enrichResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID farmId) {
        Transaction tx = load(id, farmId);
        tx.setDeletedAt(LocalDateTime.now());
        txRepo.save(tx);
        log.info("Soft-deleted transaction {} from farm {}", tx.getTransactionRef(), farmId);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionSummaryResponse summary(UUID id, UUID farmId) {
        Transaction tx = load(id, farmId);
        BigDecimal income = sumIncome(tx.getId());
        BigDecimal expense = sumExpense(tx.getId());
        BigDecimal investment = sumInvestment(tx.getId());
        BigDecimal netPosition = income.subtract(expense).subtract(investment);

        return new TransactionSummaryResponse(
                tx.getId(), tx.getTransactionRef(), tx.getStatus().name(),
                income, expense, investment,
                netPosition.signum() > 0 ? netPosition : BigDecimal.ZERO,
                netPosition.signum() < 0 ? netPosition.abs() : BigDecimal.ZERO,
                netPosition);
    }

    @Override
    @Transactional
    public int backfillFromIncome(UUID farmId, UUID userId) {
        List<Income> orphans = incomeRepo.findByFarmIdAndTransactionIdIsNull(farmId);
        if (orphans.isEmpty()) return 0;

        var byDate = orphans.stream().collect(Collectors.groupingBy(Income::getIncomeDate));

        int created = 0;
        for (var entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<Income> group = entry.getValue();

            String ref = "TXN-BF-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Transaction tx = txRepo.save(Transaction.builder()
                    .farmId(farmId).transactionRef(ref).occurredAt(date.atStartOfDay())
                    .status(TransactionStatus.DRAFT).notes("Backfilled from income on " + date)
                    .createdByUserId(userId).build());

            group.forEach(inc -> inc.setTransactionId(tx.getId()));
            incomeRepo.saveAll(group);
            created++;
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStats(UUID farmId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", txRepo.countByFarmId(farmId));
        stats.put("draft", txRepo.countByFarmIdAndStatus(farmId, TransactionStatus.DRAFT));
        stats.put("void", txRepo.countByFarmIdAndStatus(farmId, TransactionStatus.VOID));
        stats.put("posted", txRepo.countByFarmIdAndStatus(farmId, TransactionStatus.POSTED));
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public void exportToCsv(UUID farmId, PrintWriter writer) {
        List<Transaction> txs = txRepo.findByFarmIdLive(farmId, Pageable.unpaged()).getContent();
        writer.println("Reference,Date,Status,Notes,Recorded By,Total Amount");

        for (Transaction tx : txs) {
            TransactionResponse dto = enrichResponse(tx);
            writer.printf("%s,%s,%s,%s,%s,%s%n",
                    dto.getTransactionRef(),
                    dto.getOccurredAt(),
                    dto.getStatus(),
                    escapeCsv(dto.getNotes()),
                    escapeCsv(dto.getCreatedByUsername()),
                    dto.getTotalAmount()
            );
        }
    }

    private TransactionResponse enrichResponse(Transaction tx) {
        User user = tx.getCreatedByUserId() != null ? authRepository.findById(tx.getCreatedByUserId()).orElse(null) : null;
        String username = user != null ? user.getUsername() : "deleted-user";
        String role = user != null ? user.getRole() : "N/A";

        BigDecimal income = sumIncome(tx.getId());
        BigDecimal expense = sumExpense(tx.getId());
        BigDecimal investment = sumInvestment(tx.getId());

        BigDecimal totalAmount = income.add(expense).add(investment);
        String primaryType = "MIXED";
        if (income.compareTo(BigDecimal.ZERO) > 0 && expense.compareTo(BigDecimal.ZERO) == 0 && investment.compareTo(BigDecimal.ZERO) == 0) primaryType = "INCOME";
        else if (expense.compareTo(BigDecimal.ZERO) > 0 && income.compareTo(BigDecimal.ZERO) == 0 && investment.compareTo(BigDecimal.ZERO) == 0) primaryType = "EXPENSE";
        else if (investment.compareTo(BigDecimal.ZERO) > 0 && income.compareTo(BigDecimal.ZERO) == 0 && expense.compareTo(BigDecimal.ZERO) == 0) primaryType = "INVESTMENT";

        return mapper.toResponse(tx, username, role, totalAmount, primaryType);
    }

    private Transaction load(UUID id, UUID farmId) {
        return txRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with ID: " + id));
    }

    private BigDecimal sumIncome(UUID txId) {
        return incomeRepo.findByTransactionIdLive(txId).stream().map(Income::getAmountReceived)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumExpense(UUID txId) {
        return expenseRepo.findByTransactionIdLive(txId).stream().map(Expense::getAmountPaid)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumInvestment(UUID txId) {
        return investmentRepo.findByTransactionIdLive(txId).stream().map(Investment::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generateRef(UUID farmId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String candidate;
        int attempt = 0;
        do {
            candidate = "TXN-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            attempt++;
        } while (txRepo.existsByFarmIdAndTransactionRefLive(farmId, candidate) && attempt < 10);
        return candidate;
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.contains(",") ? "\"" + val.replace("\"", "\"\"") + "\"" : val;
    }
}