package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.calculator.SpeciesKey;
import com.agriva.feeding.mapper.FeedBatchMapper;
import com.agriva.feeding.mapper.FormulationMapper;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.request.ProduceFeedFromForecastRequest;
import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.dto.response.ProduceFeedFromForecastResponse;
import com.agriva.feeding.model.entity.DemandForecast;
import com.agriva.feeding.model.entity.FeedBatch;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.service.FormulationService;
import com.agriva.feeding.service.ProductionService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.IdempotencyKeyCollisionException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastProduceService {

    private static final DateTimeFormatter AUTO_NAME_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DemandForecastRepository forecastRepository;
    private final FormulationTemplateRepository templateRepository;
    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository formulationLineRepository;
    private final FeedBatchRepository feedBatchRepository;
    private final FormulationService formulationService;
    private final ProductionService productionService;
    private final FormulationMapper formulationMapper;
    private final FeedBatchMapper feedBatchMapper;

    @Transactional
    public ProduceFeedFromForecastResponse produceFeedFromForecast(
            UUID forecastId,
            ProduceFeedFromForecastRequest request,
            UUID farmId,
            UUID userId,
            String idempotencyKey) {

        // 1. Fast-path pre-check — the common replay case.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = feedBatchRepository.findByIdempotencyKeyAndFarmId(
                    idempotencyKey, farmId);
            if (existing.isPresent()) {
                log.info("Produce-feed replayed for key {} — returning existing batch {}",
                        idempotencyKey, existing.get().getId());
                return replay(existing.get(), farmId);
            }
        }

        // 2. Lock the forecast.
        DemandForecast forecast = forecastRepository
                .findByIdAndFarmIdLiveForUpdate(forecastId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Forecast not found with ID: " + forecastId));

        // 2b. Re-check inside the lock. B5: this catches the concurrent case
        //     where another request with the same key committed while we were
        //     waiting for the forecast lock. Avoids wasted formulation creation.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = feedBatchRepository.findByIdempotencyKeyAndFarmId(
                    idempotencyKey, farmId);
            if (existing.isPresent()) {
                log.info("Produce-feed replayed for key {} after lock — batch {}",
                        idempotencyKey, existing.get().getId());
                return replay(existing.get(), farmId);
            }
        }

        // 3. Resolve template.
        UUID templateId = resolveTemplateIdForProduce(forecast, farmId);

        // 4. Build formulation request. Auto-name includes a UUID suffix (B5)
        //    so concurrent attempts never collide on the formulation-name
        //    unique index — the loser of the race fails on
        //    ux_feed_batch_idempotency instead, which translates to a replay.
        String formulationName = (request.getFormulationName() != null
                && !request.getFormulationName().isBlank())
                ? request.getFormulationName().trim()
                : autoFormulationName(forecast);

        FormulationRequest formReq = new FormulationRequest();
        formReq.setName(formulationName);
        formReq.setTargetSpecies(forecast.getSpecies());
        formReq.setMode(FormulationMode.ALGORITHM);
        formReq.setTargetQuantityKg(request.getProducedKg());
        formReq.setTargetCpValue(forecast.getTargetCpValue());
        formReq.setFormulaTemplateId(templateId);
        formReq.setForecastId(forecastId);

        // 5. Create formulation (same transaction).
        FormulationResponse formulationResp =
                formulationService.createFormulation(formReq, farmId, userId);

        // 6. Build production request.
        LocalDate productionDate = request.getProductionDate() != null
                ? request.getProductionDate() : LocalDate.now();

        ProductionRequest prodReq = new ProductionRequest();
        prodReq.setFormulationId(formulationResp.getId());
        prodReq.setProducedKg(request.getProducedKg());
        prodReq.setProductionDate(productionDate);
        prodReq.setForecastId(forecastId);
        prodReq.setIdempotencyKey(
                (idempotencyKey != null && !idempotencyKey.isBlank())
                        ? idempotencyKey : null);
        prodReq.setNotes(request.getNotes());

        FeedBatchResponse batchResp;
        try {
            batchResp = productionService.produce(prodReq, farmId, userId);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()
                    && isIdempotencyKeyViolation(e)) {
                log.info("Idempotency-Key {} race — transaction will roll back, "
                        + "controller will replay", idempotencyKey);
                throw new IdempotencyKeyCollisionException(idempotencyKey);
            }
            throw e;
        }

        log.info("Produce-from-forecast success: forecast={} formulation={} batch={} "
                        + "produced={}kg by user {}",
                forecastId, formulationResp.getId(), batchResp.getId(),
                request.getProducedKg(), userId);

        return ProduceFeedFromForecastResponse.builder()
                .formulation(formulationResp)
                .feedBatch(batchResp)
                .replayed(false)
                .build();
    }

    @Transactional(readOnly = true)
    public ProduceFeedFromForecastResponse replayByKey(UUID farmId, String idempotencyKey) {
        FeedBatch batch = feedBatchRepository.findByIdempotencyKeyAndFarmId(idempotencyKey, farmId)
                .orElseThrow(() -> new IllegalStateException(
                        "Replay requested for key " + idempotencyKey
                                + " but no batch exists on farm " + farmId));
        return replay(batch, farmId);
    }

    private ProduceFeedFromForecastResponse replay(FeedBatch batch, UUID farmId) {
        Formulation formulation = formulationRepository
                .findByIdAndFarmIdLive(batch.getFormulationId(), farmId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotent replay: batch " + batch.getId()
                                + " references formulation " + batch.getFormulationId()
                                + " that is not found on farm " + farmId));

        List<FormulationLine> lines = formulationLineRepository
                .findByFormulationIdOrderByPercentageDesc(formulation.getId());

        return ProduceFeedFromForecastResponse.builder()
                .formulation(formulationMapper.toResponse(formulation, lines))
                .feedBatch(feedBatchMapper.toResponse(batch))
                .replayed(true)
                .build();
    }

    private UUID resolveTemplateIdForProduce(DemandForecast forecast, UUID farmId) {
        if (forecast.getFormulaTemplateId() != null) {
            templateRepository
                    .findByIdOrSystemForFarm(forecast.getFormulaTemplateId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Forecast references template " + forecast.getFormulaTemplateId()
                                    + " which no longer exists or is not accessible on this farm"));
            return forecast.getFormulaTemplateId();
        }

        String expectedName = SpeciesKey.defaultTemplateNameFor(forecast.getSpecies());

        return templateRepository.findSystemTemplatesLive().stream()
                .filter(t -> expectedName.equals(t.getName()))
                .findFirst()
                .map(t -> t.getId())
                .orElseThrow(() -> new BusinessException(
                        "Forecast has no formulaTemplateId and no default template '"
                                + expectedName + "' is seeded. Set formulaTemplateId on "
                                + "the forecast first (PUT /forecasts/{id})."));
    }

    private String autoFormulationName(DemandForecast forecast) {
        String shortId = forecast.getId().toString().substring(0, 8);
        String ts = LocalDateTime.now().format(AUTO_NAME_TS);
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return forecast.getSpecies() + " Feed " + shortId + "-" + ts + "-" + unique;
    }

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("ux_feed_batch_idempotency");
    }
}