package com.agriva.service.impl;

import com.agriva.exception.InvestmentNotFoundException;
import com.agriva.mapper.InvestmentMapper;
import com.agriva.model.dto.investment.InvestmentRequest;
import com.agriva.model.dto.investment.InvestmentResponse;
import com.agriva.model.entity.Investment;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.InvestmentRepository;
import com.agriva.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentServiceImpl implements InvestmentService {

    private final InvestmentRepository investmentRepo;
    private final AuthRepository authRepository;
    private final InvestmentMapper mapper;

    @Override
    @Transactional
    public InvestmentResponse createInvestment(InvestmentRequest request, UUID farmId, UUID userId) {
        Investment investment = Investment.builder()
                .farmId(farmId)
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .currency(request.getCurrency() == null ? "USD" : request.getCurrency())
                .investorName(request.getInvestorName())
                .type(request.getType())
                .description(request.getDescription())
                .investmentDate(request.getInvestmentDate())
                .createdByUserId(userId)
                .active(false)
                .build();

        Investment saved = investmentRepo.saveAndFlush(investment);

        if (request.isActivate()) {
            saved = doActivate(saved);
        }

        log.info("Investment {} created for farm {} (active={})",
                saved.getId(), farmId, saved.isActive());
        return toResponseWithSpend(saved);
    }

    @Override
    public Page<InvestmentResponse> listInvestments(UUID farmId, Pageable pageable) {
        Page<Investment> page = investmentRepo.findByFarmId(farmId, pageable);
        Map<UUID, User> userById = lookupUsers(page.getContent());
        return page.map(inv -> mapper.toResponse(
                inv,
                userById,
                investmentRepo.sumExpensesByInvestmentIdOrZero(inv.getId())));
    }

    @Override
    public InvestmentResponse getInvestment(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        return toResponseWithSpend(investment);
    }

    @Override
    @Transactional
    public InvestmentResponse updateInvestment(UUID id, InvestmentRequest request, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));

        // Guard: cannot shrink below already-spent amount
        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(id);
        if (request.getAmount().compareTo(spent) < 0) {
            throw new IllegalArgumentException(
                    "Cannot reduce investment below already-spent amount of " + spent.toPlainString());
        }

        investment.setAmount(request.getAmount());
        investment.setTransactionId(request.getTransactionId());
        investment.setCurrency(request.getCurrency() == null ? "USD" : request.getCurrency());
        investment.setInvestorName(request.getInvestorName());
        investment.setType(request.getType());
        investment.setDescription(request.getDescription());
        investment.setInvestmentDate(request.getInvestmentDate());

        Investment saved = investmentRepo.saveAndFlush(investment);

        if (request.isActivate() && !saved.isActive()) {
            saved = doActivate(saved);
        }

        log.info("Investment {} updated for farm {}", id, farmId);
        return toResponseWithSpend(saved);
    }

    @Override
    @Transactional
    public void deleteInvestment(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));

        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(id);
        if (spent.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete investment with linked expenses. Spent: " + spent.toPlainString());
        }

        investmentRepo.delete(investment);
        log.info("Investment {} deleted from farm {}", id, farmId);
    }

    @Override
    @Transactional
    public InvestmentResponse activate(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        return toResponseWithSpend(doActivate(investment));
    }

    @Override
    @Transactional
    public InvestmentResponse deactivate(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        if (investment.isActive()) {
            investment.setActive(false);
            investment.setActivatedAt(null);
            investmentRepo.saveAndFlush(investment);
            log.info("Investment {} deactivated for farm {}", id, farmId);
        }
        return toResponseWithSpend(investment);
    }

    @Override
    public InvestmentResponse getActive(UUID farmId) {
        return investmentRepo.findFirstByFarmIdAndActiveTrue(farmId)
                .map(this::toResponseWithSpend)
                .orElse(null);
    }

    // -------------------------------------------------------------------

    /**
     * Deactivates the current active investment (if any) and activates the given one.
     * Runs two flushes to satisfy the partial unique index (one active per farm).
     */
    private Investment doActivate(Investment target) {
        if (target.isActive()) {
            return target;
        }

        investmentRepo.findFirstByFarmIdAndActiveTrue(target.getFarmId()).ifPresent(current -> {
            current.setActive(false);
            current.setActivatedAt(null);
            investmentRepo.saveAndFlush(current);
        });

        target.setActive(true);
        target.setActivatedAt(LocalDateTime.now());
        Investment saved = investmentRepo.saveAndFlush(target);
        log.info("Investment {} is now the active investment for farm {}",
                saved.getId(), saved.getFarmId());
        return saved;
    }

    private InvestmentResponse toResponseWithSpend(Investment inv) {
        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(inv.getId());
        return mapper.toResponse(inv, lookupUsers(List.of(inv)), spent);
    }

    private Map<UUID, User> lookupUsers(List<Investment> investments) {
        Set<UUID> userIds = investments.stream()
                .map(Investment::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return authRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }
}