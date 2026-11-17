package com.agriva.feeding.service.impl;

import com.agriva.feeding.kafka.producer.*;
import com.agriva.feeding.model.dto.request.*;
import com.agriva.feeding.model.dto.response.*;
import com.agriva.feeding.model.entity.*;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FeedBulkService;
import com.agriva.feeding.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedBulkServiceImpl implements FeedBulkService {

    private final FeedIngredientExcelParser ingredientParser;
    private final FeedFormulationExcelParser formulationParser;
    private final FeedProductionExcelParser productionParser;
    private final FeedFeedingExcelParser feedingParser;

    private final FeedIngredientProducer ingredientProducer;
    private final FeedFormulationProducer formulationProducer;
    private final FeedProductionProducer productionProducer;
    private final FeedFeedingProducer feedingProducer;

    private final StagedFeedingIngredientRepository stagedIngredientRepo;
    private final StagedFeedingFormulationRepository stagedFormulationRepo;
    private final StagedFeedingProductionRepository stagedProductionRepo;
    private final StagedFeedingEventRepository stagedEventRepo;

    private final ObjectMapper objectMapper;

    @Override
    public FeedBulkUploadResponse bulkUploadIngredients(MultipartFile file,
                                                        UUID farmId, UUID userId, String email) {
        List<FeedIngredientExcelParser.ParsedRow> rows = parseOrThrow(
                () -> ingredientParser.parse(file.getInputStream()));
        FeedBulkUploadResponse resp = new FeedBulkUploadResponse();
        resp.setTotalRows(rows.size());
        for (var row : rows) {
            if (row.isError()) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                ingredientProducer.publish(new FeedIngredientSubmissionEvent(
                        submissionId, farmId, userId, email,
                        objectMapper.writeValueAsString(row.payload()), LocalDateTime.now()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        log.info("Bulk feed-ingredient upload farm={} total={} queued={} failed={}",
                farmId, resp.getTotalRows(), resp.getQueued(), resp.getFailed());
        return resp;
    }

    @Override
    public FeedBulkUploadResponse bulkUploadFormulations(MultipartFile file,
                                                         UUID farmId, UUID userId, String email) {
        List<FeedFormulationExcelParser.ParsedRow> rows = parseOrThrow(
                () -> formulationParser.parse(file.getInputStream()));
        FeedBulkUploadResponse resp = new FeedBulkUploadResponse();
        resp.setTotalRows(rows.size());
        for (var row : rows) {
            if (row.isError()) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                formulationProducer.publish(new FeedFormulationSubmissionEvent(
                        submissionId, farmId, userId, email,
                        objectMapper.writeValueAsString(row.payload()), LocalDateTime.now()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        return resp;
    }

    @Override
    public FeedBulkUploadResponse bulkUploadProductions(MultipartFile file,
                                                        UUID farmId, UUID userId, String email) {
        List<FeedProductionExcelParser.ParsedRow> rows = parseOrThrow(
                () -> productionParser.parse(file.getInputStream()));
        FeedBulkUploadResponse resp = new FeedBulkUploadResponse();
        resp.setTotalRows(rows.size());
        for (var row : rows) {
            if (row.isError()) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                productionProducer.publish(new FeedProductionSubmissionEvent(
                        submissionId, farmId, userId, email,
                        objectMapper.writeValueAsString(row.payload()), LocalDateTime.now()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        return resp;
    }

    @Override
    public FeedBulkUploadResponse bulkUploadFeedings(MultipartFile file,
                                                     UUID farmId, UUID userId, String email) {
        List<FeedFeedingExcelParser.ParsedRow> rows = parseOrThrow(
                () -> feedingParser.parse(file.getInputStream()));
        FeedBulkUploadResponse resp = new FeedBulkUploadResponse();
        resp.setTotalRows(rows.size());
        for (var row : rows) {
            if (row.isError()) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                feedingProducer.publish(new FeedFeedingSubmissionEvent(
                        submissionId, farmId, userId, email,
                        objectMapper.writeValueAsString(row.payload()), LocalDateTime.now()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedFeedingIngredientResponse> listStagedIngredients(
            UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedIngredientRepo.findByFarmId(farmId, pageable)
                : stagedIngredientRepo.findByFarmIdAndStatus(
                farmId, StagedFeedingStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toIngredientResp);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedFeedingFormulationResponse> listStagedFormulations(
            UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedFormulationRepo.findByFarmId(farmId, pageable)
                : stagedFormulationRepo.findByFarmIdAndStatus(
                farmId, StagedFeedingStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toFormulationResp);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedFeedingProductionResponse> listStagedProductions(
            UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedProductionRepo.findByFarmId(farmId, pageable)
                : stagedProductionRepo.findByFarmIdAndStatus(
                farmId, StagedFeedingStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toProductionResp);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedFeedingEventResponse> listStagedFeedings(
            UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedEventRepo.findByFarmId(farmId, pageable)
                : stagedEventRepo.findByFarmIdAndStatus(
                farmId, StagedFeedingStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toEventResp);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> ingredientsStats(UUID farmId) { return stats(stagedIngredientRepo, farmId); }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> formulationsStats(UUID farmId) { return stats(stagedFormulationRepo, farmId); }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> productionsStats(UUID farmId) { return stats(stagedProductionRepo, farmId); }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> feedingsStats(UUID farmId) { return stats(stagedEventRepo, farmId); }

    private Map<String, Long> stats(StagedFeedingIngredientRepository r, UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (var s : StagedFeedingStatus.values())
            out.put(s.name(), r.countByFarmIdAndStatus(farmId, s));
        return out;
    }
    private Map<String, Long> stats(StagedFeedingFormulationRepository r, UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (var s : StagedFeedingStatus.values())
            out.put(s.name(), r.countByFarmIdAndStatus(farmId, s));
        return out;
    }
    private Map<String, Long> stats(StagedFeedingProductionRepository r, UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (var s : StagedFeedingStatus.values())
            out.put(s.name(), r.countByFarmIdAndStatus(farmId, s));
        return out;
    }
    private Map<String, Long> stats(StagedFeedingEventRepository r, UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (var s : StagedFeedingStatus.values())
            out.put(s.name(), r.countByFarmIdAndStatus(farmId, s));
        return out;
    }

    private StagedFeedingIngredientResponse toIngredientResp(StagedFeedingIngredient s) {
        return new StagedFeedingIngredientResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(), s.getValidationErrors(),
                s.getPayloadJson(), s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedIngredientId(), s.getRetryCount());
    }
    private StagedFeedingFormulationResponse toFormulationResp(StagedFeedingFormulation s) {
        return new StagedFeedingFormulationResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(), s.getValidationErrors(),
                s.getPayloadJson(), s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedFormulationId(), s.getRetryCount());
    }
    private StagedFeedingProductionResponse toProductionResp(StagedFeedingProduction s) {
        return new StagedFeedingProductionResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(), s.getValidationErrors(),
                s.getPayloadJson(), s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedBatchId(), s.getRetryCount());
    }
    private StagedFeedingEventResponse toEventResp(StagedFeedingEvent s) {
        return new StagedFeedingEventResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(), s.getValidationErrors(),
                s.getPayloadJson(), s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedFeedingId(), s.getRetryCount());
    }

    private static <T> List<T> parseOrThrow(ParserCall<T> call) {
        if (call == null) throw new IllegalArgumentException("Excel file is required");
        try { return call.parse(); }
        catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read Excel: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface ParserCall<T> {
        List<T> parse() throws java.io.IOException;
    }
}