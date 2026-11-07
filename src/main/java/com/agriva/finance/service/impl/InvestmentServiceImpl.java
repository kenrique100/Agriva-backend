package com.agriva.finance.service.impl;

import com.agriva.shared.exception.InvestmentNotFoundException;
import com.agriva.finance.mapper.InvestmentMapper;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.dto.response.InvestmentResponse;
import com.agriva.finance.model.entity.Investment;
import com.agriva.identity.models.entity.User;
import com.agriva.shared.models.enums.Currency;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.service.InvestmentService;
import com.agriva.shared.models.ref.UserRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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
                .currency(request.getCurrency() == null ? Currency.USD : request.getCurrency())
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
    @Transactional(readOnly = true)
    public Page<InvestmentResponse> listInvestments(UUID farmId, Pageable pageable) {
        Page<Investment> page = investmentRepo.findByFarmIdLive(farmId, pageable);
        Map<UUID, UserRef> userById = lookupUsers(page.getContent());

        List<UUID> ids = page.getContent().stream().map(Investment::getId).toList();
        Map<UUID, BigDecimal> spentById = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : investmentRepo.sumExpensesByInvestmentIdIn(ids)) {
                spentById.put((UUID) r[0], (BigDecimal) r[1]);
            }
        }

        return page.map(inv -> mapper.toResponse(
                inv, userById, spentById.getOrDefault(inv.getId(), BigDecimal.ZERO)));
    }

    @Override
    @Transactional(readOnly = true)
    public InvestmentResponse getInvestment(UUID id, UUID farmId) {
        Investment inv = investmentRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        return toResponseWithSpend(inv);
    }

    @Override
    @Transactional(readOnly = true)
    public InvestmentResponse getActive(UUID farmId) {
        return investmentRepo.findFirstByFarmIdAndActiveTrue(farmId)
                .map(this::toResponseWithSpend)
                .orElse(null);
    }

    @Override
    @Transactional
    public InvestmentResponse updateInvestment(UUID id, InvestmentRequest request, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));

        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(id);
        if (request.getAmount().compareTo(spent) < 0) {
            throw new IllegalArgumentException(
                    "Cannot reduce investment below already-spent amount of "
                            + spent.toPlainString());
        }

        investment.setAmount(request.getAmount());
        investment.setTransactionId(request.getTransactionId());
        investment.setCurrency(request.getCurrency() == null
                ? Currency.USD : request.getCurrency());
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
        Investment investment = investmentRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));

        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(id);
        if (spent.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete investment with linked expenses. Spent: "
                            + spent.toPlainString());
        }

        investment.setDeletedAt(LocalDateTime.now());
        investment.setActive(false);
        investmentRepo.save(investment);
        log.info("Soft-deleted investment {} from farm {}", id, farmId);
    }

    @Override
    @Transactional
    public InvestmentResponse activate(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        return toResponseWithSpend(doActivate(investment));
    }

    @Override
    @Transactional
    public InvestmentResponse deactivate(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        if (investment.isActive()) {
            investment.setActive(false);
            investment.setActivatedAt(null);
            investmentRepo.saveAndFlush(investment);
            log.info("Investment {} deactivated for farm {}", id, farmId);
        }
        return toResponseWithSpend(investment);
    }

    private Investment doActivate(Investment target) {
        if (target.isActive()) return target;

        investmentRepo.findActiveForUpdate(target.getFarmId()).ifPresent(current -> {
            current.setActive(false);
            current.setActivatedAt(null);
            investmentRepo.saveAndFlush(current);
        });

        target.setActive(true);
        target.setActivatedAt(LocalDateTime.now());
        Investment saved = investmentRepo.saveAndFlush(target);
        log.info("Investment {} is now active for farm {}", saved.getId(), saved.getFarmId());
        return saved;
    }

    private InvestmentResponse toResponseWithSpend(Investment inv) {
        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(inv.getId());
        return mapper.toResponse(inv, lookupUsers(List.of(inv)), spent);
    }

    private Map<UUID, UserRef> lookupUsers(List<Investment> investments) {
        Set<UUID> userIds = investments.stream()
                .map(Investment::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return authRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> new UserRef(
                                u.getId(), u.getEmail(), u.getUsername(),
                                u.getRole(), u.getFarmId(),
                                u.getCreatedAt(), u.getUpdatedAt()),
                        (a, b) -> a));
    }
}