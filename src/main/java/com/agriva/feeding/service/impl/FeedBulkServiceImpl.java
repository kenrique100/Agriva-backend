package com.agriva.feeding.service.impl;

import com.agriva.feeding.kafka.producer.*;
import com.agriva.feeding.model.dto.request.*;
import com.agriva.feeding.model.dto.response.*;
import com.agriva.feeding.model.entity.*;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FeedBulkService;
import com.agriva.feeding.utils.*;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.enums.StagedStatus;
import com.agriva.shared.utils.BatchParsedRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedBulkServiceImpl implements FeedBulkService {

    private final IngredientExcelParser ingredientParser;
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
    public FeedBulkUploadResponse bulkUploadIngredients(MultipartFile file, UUID farmId, UUID userId, String email) {
        List<BatchParsedRow<IngredientSubmissionPayload>> rows =
                parseOrThrow(() -> ingredientParser.parse(file.getInputStream()));

        UUID batchId = UUID.randomUUID();
        FeedBulkUploadResponse resp = newResponse(rows.size(), batchId);

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
                        objectMapper.writeValueAsString(row.request()),
                        LocalDateTime.now(), batchId, row.rowNumber()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        log.info("Bulk feed-ingredient upload {} farm={} total={} queued={} failed={}",
                batchId, farmId, resp.getTotalRows(), resp.getQueued(), resp.getFailed());
        return resp;
    }

    @Override
    public FeedBulkUploadResponse bulkUploadFormulations(MultipartFile file, UUID farmId, UUID userId, String email) {
        List<FeedFormulationExcelParser.ParsedRow> rows =
                parseOrThrow(() -> formulationParser.parse(file.getInputStream()));

        UUID batchId = UUID.randomUUID();
        FeedBulkUploadResponse resp = newResponse(rows.size(), batchId);

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
                        objectMapper.writeValueAsString(row.payload()),
                        LocalDateTime.now(), batchId, row.rowNumber()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        log.info("Bulk feed-formulation upload {} farm={} total={} queued={} failed={}",
                batchId, farmId, resp.getTotalRows(), resp.getQueued(), resp.getFailed());
        return resp;
    }

    @Override
    public FeedBulkUploadResponse bulkUploadProductions(MultipartFile file, UUID farmId, UUID userId, String email) {
        List<FeedProductionExcelParser.ParsedRow> rows =
                parseOrThrow(() -> productionParser.parse(file.getInputStream()));

        UUID batchId = UUID.randomUUID();
        FeedBulkUploadResponse resp = newResponse(rows.size(), batchId);

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
                        objectMapper.writeValueAsString(row.payload()),
                        LocalDateTime.now(), batchId, row.rowNumber()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        log.info("Bulk feed-production upload {} farm={} total={} queued={} failed={}",
                batchId, farmId, resp.getTotalRows(), resp.getQueued(), resp.getFailed());
        return resp;
    }

    @Override
    public FeedBulkUploadResponse bulkUploadFeedings(MultipartFile file, UUID farmId, UUID userId, String email) {
        List<FeedFeedingExcelParser.ParsedRow> rows =
                parseOrThrow(() -> feedingParser.parse(file.getInputStream()));

        UUID batchId = UUID.randomUUID();
        FeedBulkUploadResponse resp = newResponse(rows.size(), batchId);

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
                        objectMapper.writeValueAsString(row.payload()),
                        LocalDateTime.now(), batchId, row.rowNumber()));
                resp.setQueued(resp.getQueued() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                resp.setFailed(resp.getFailed() + 1);
                resp.getResults().add(new FeedBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        log.info("Bulk feed-feeding upload {} farm={} total={} queued={} failed={}",
                batchId, farmId, resp.getTotalRows(), resp.getQueued(), resp.getFailed());
        return resp;
    }

    @Override @Transactional(readOnly = true)
    public Page<StagedFeedingIngredientResponse> listStagedIngredients(UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedIngredientRepo.findByFarmId(farmId, pageable)
                : stagedIngredientRepo.findByFarmIdAndStatus(
                farmId, StagedStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toIngredientResp);
    }

    @Override @Transactional(readOnly = true)
    public Page<StagedFeedingFormulationResponse> listStagedFormulations(UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedFormulationRepo.findByFarmId(farmId, pageable)
                : stagedFormulationRepo.findByFarmIdAndStatus(
                farmId, StagedStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toFormulationResp);
    }

    @Override @Transactional(readOnly = true)
    public Page<StagedFeedingProductionResponse> listStagedProductions(UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedProductionRepo.findByFarmId(farmId, pageable)
                : stagedProductionRepo.findByFarmIdAndStatus(
                farmId, StagedStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toProductionResp);
    }

    @Override @Transactional(readOnly = true)
    public Page<StagedFeedingEventResponse> listStagedFeedings(UUID farmId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? stagedEventRepo.findByFarmId(farmId, pageable)
                : stagedEventRepo.findByFarmIdAndStatus(
                farmId, StagedStatus.valueOf(status.toUpperCase()), pageable);
        return page.map(this::toEventResp);
    }

    @Override @Transactional(readOnly = true)
    public BatchStatusResponse bulkStatusIngredients(UUID batchId, UUID farmId) {
        List<StagedFeedingIngredient> rows =
                stagedIngredientRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId);
        return buildBatchStatus(batchId, rows, StagedFeedingIngredient::getStatus,
                StagedFeedingIngredient::getRowNumber,
                StagedFeedingIngredient::getId,
                StagedFeedingIngredient::getValidationErrors);
    }

    @Override @Transactional(readOnly = true)
    public BatchStatusResponse bulkStatusFormulations(UUID batchId, UUID farmId) {
        List<StagedFeedingFormulation> rows =
                stagedFormulationRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId);
        return buildBatchStatus(batchId, rows, StagedFeedingFormulation::getStatus,
                StagedFeedingFormulation::getRowNumber,
                StagedFeedingFormulation::getId,
                StagedFeedingFormulation::getValidationErrors);
    }

    @Override @Transactional(readOnly = true)
    public BatchStatusResponse bulkStatusProductions(UUID batchId, UUID farmId) {
        List<StagedFeedingProduction> rows =
                stagedProductionRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId);
        return buildBatchStatus(batchId, rows, StagedFeedingProduction::getStatus,
                StagedFeedingProduction::getRowNumber,
                StagedFeedingProduction::getId,
                StagedFeedingProduction::getValidationErrors);
    }

    @Override @Transactional(readOnly = true)
    public BatchStatusResponse bulkStatusFeedings(UUID batchId, UUID farmId) {
        List<StagedFeedingEvent> rows =
                stagedEventRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId);
        return buildBatchStatus(batchId, rows, StagedFeedingEvent::getStatus,
                StagedFeedingEvent::getRowNumber,
                StagedFeedingEvent::getId,
                StagedFeedingEvent::getValidationErrors);
    }

    private <T> BatchStatusResponse buildBatchStatus(
            UUID batchId,
            List<T> rows,
            Function<T, StagedStatus> statusFn,
            Function<T, Integer> rowNumberFn,
            Function<T, UUID> idFn,
            Function<T, String> errorFn) {

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Batch not found: " + batchId);
        }
        int pending = 0, moved = 0, failed = 0;
        List<BatchStatusResponse.FailedRow> failedRows = new ArrayList<>();
        for (T row : rows) {
            switch (statusFn.apply(row)) {
                case PENDING, RETRYING -> pending++;
                case MOVED -> moved++;
                case FAILED -> {
                    failed++;
                    failedRows.add(new BatchStatusResponse.FailedRow(
                            rowNumberFn.apply(row), idFn.apply(row), errorFn.apply(row)));
                }
            }
        }
        return BatchStatusResponse.builder()
                .batchId(batchId)
                .total(rows.size())
                .pending(pending)
                .moved(moved)
                .failed(failed)
                .failedRows(failedRows.isEmpty() ? null : failedRows)
                .build();
    }

    @FunctionalInterface
    private interface StagedStatusCounter {
        long count(UUID farmId, StagedStatus status);
    }

    private Map<String, Long> stats(StagedStatusCounter counter, UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedStatus s : StagedStatus.values()) {
            out.put(s.name(), counter.count(farmId, s));
        }
        return out;
    }

    @Override @Transactional(readOnly = true)
    public Map<String, Long> ingredientsStats(UUID farmId) {
        return stats(stagedIngredientRepo::countByFarmIdAndStatus, farmId);
    }

    @Override @Transactional(readOnly = true)
    public Map<String, Long> formulationsStats(UUID farmId) {
        return stats(stagedFormulationRepo::countByFarmIdAndStatus, farmId);
    }

    @Override @Transactional(readOnly = true)
    public Map<String, Long> productionsStats(UUID farmId) {
        return stats(stagedProductionRepo::countByFarmIdAndStatus, farmId);
    }

    @Override @Transactional(readOnly = true)
    public Map<String, Long> feedingsStats(UUID farmId) {
        return stats(stagedEventRepo::countByFarmIdAndStatus, farmId);
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

    private static FeedBulkUploadResponse newResponse(int totalRows, UUID batchId) {
        FeedBulkUploadResponse resp = new FeedBulkUploadResponse();
        resp.setBatchId(batchId);
        resp.setTotalRows(totalRows);
        return resp;
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