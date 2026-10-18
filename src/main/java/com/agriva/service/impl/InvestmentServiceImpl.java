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
                .build();

        // saveAndFlush ensures Hibernate populates @CreationTimestamp before mapping
        Investment saved = investmentRepo.saveAndFlush(investment);
        log.info("Investment {} created for farm {}", saved.getId(), farmId);
        return mapper.toResponse(saved, lookupUsers(List.of(saved)));
    }

    @Override
    public Page<InvestmentResponse> listInvestments(UUID farmId, Pageable pageable) {
        Page<Investment> page = investmentRepo.findByFarmId(farmId, pageable);
        Map<UUID, User> userById = lookupUsers(page.getContent());
        return page.map(inv -> mapper.toResponse(inv, userById));
    }

    @Override
    public InvestmentResponse getInvestment(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        return mapper.toResponse(investment, lookupUsers(List.of(investment)));
    }

    @Override
    @Transactional
    public InvestmentResponse updateInvestment(UUID id, InvestmentRequest request, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));

        investment.setAmount(request.getAmount());
        investment.setTransactionId(request.getTransactionId());
        investment.setCurrency(request.getCurrency() == null ? "USD" : request.getCurrency());
        investment.setInvestorName(request.getInvestorName());
        investment.setType(request.getType());
        investment.setDescription(request.getDescription());
        investment.setInvestmentDate(request.getInvestmentDate());

        Investment saved = investmentRepo.saveAndFlush(investment);
        log.info("Investment {} updated for farm {}", id, farmId);
        return mapper.toResponse(saved, lookupUsers(List.of(saved)));
    }

    @Override
    @Transactional
    public void deleteInvestment(UUID id, UUID farmId) {
        Investment investment = investmentRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new InvestmentNotFoundException("Investment not found with ID: " + id));
        investmentRepo.delete(investment);
        log.info("Investment {} deleted from farm {}", id, farmId);
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