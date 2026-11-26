package com.agriva.feeding.service.impl;

import com.agriva.feeding.mapper.FormulationMapper;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.service.FormulationComputationService;
import com.agriva.feeding.service.FormulationExpenseService;
import com.agriva.feeding.service.FormulationService;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.shared.models.enums.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormulationServiceImpl implements FormulationService {

    private final FormulationComputationService computationService;
    private final FormulationExpenseService expenseService;
    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository lineRepository;
    private final IngredientRepository ingredientRepository;
    private final FormulationMapper mapper;

    @Override
    @Transactional
    public FormulationResponse createFormulation(FormulationRequest request,
                                                 UUID farmId, UUID userId) {
        // 1. Compute + persist the formulation and its lines.
        FormulationComputationService.Computed computed =
                computationService.computeAndPersist(request, farmId, userId);

        // 2. Snapshot CP + book the forward expense.
        Map<UUID, Currency> currencyById = ingredientRepository.findAllByFarmIdLive(farmId).stream()
                .filter(i -> i.getCurrency() != null)
                .collect(Collectors.toMap(
                        Ingredient::getId, Ingredient::getCurrency, (a, b) -> a));

        expenseService.snapshotCpAndBookExpense(
                computed.formulation(), computed.lines(), farmId, userId, currencyById);

        return computationService.toResponse(computed);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FormulationResponse> listFormulations(UUID farmId, Pageable pageable) {
        Page<Formulation> page = formulationRepository.findByFarmIdLive(farmId, pageable);
        if (page.isEmpty()) return page.map(f -> mapper.toResponse(f, List.of()));

        List<UUID> ids = page.getContent().stream().map(Formulation::getId).toList();
        Map<UUID, List<FormulationLine>> linesByFormulation =
                lineRepository.findByFormulationIdInOrderByFormulationIdAscPercentageDesc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(FormulationLine::getFormulationId));

        return page.map(f -> mapper.toResponse(
                f, linesByFormulation.getOrDefault(f.getId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public FormulationResponse getFormulation(UUID id, UUID farmId) {
        Formulation f = formulationRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + id));
        return mapper.toResponse(f,
                lineRepository.findByFormulationIdOrderByPercentageDesc(id));
    }

    @Override
    @Transactional
    public void deleteFormulation(UUID id, UUID farmId, UUID userId) {
        Formulation f = formulationRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + id));

        f.setDeletedAt(LocalDateTime.now());
        formulationRepository.save(f);

        expenseService.scheduleReversal(f, farmId, userId);

        log.info("Formulation {} soft-deleted from farm {} (expense reversal scheduled)",
                id, farmId);
    }
}