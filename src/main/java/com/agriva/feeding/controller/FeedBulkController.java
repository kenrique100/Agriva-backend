package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.response.*;
import com.agriva.feeding.service.FeedBulkService;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feeding")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.bulk.enabled",
        havingValue = "true", matchIfMissing = true)
public class FeedBulkController {

    private final FeedBulkService bulkService;

    @PostMapping(value = "/bulk/ingredients", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FeedBulkUploadResponse> bulkIngredients(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(bulkService.bulkUploadIngredients(
                file, u.farmId(), u.userId(), u.email()));
    }

    @PostMapping(value = "/bulk/formulations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FeedBulkUploadResponse> bulkFormulations(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(bulkService.bulkUploadFormulations(
                file, u.farmId(), u.userId(), u.email()));
    }

    @PostMapping(value = "/bulk/productions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FeedBulkUploadResponse> bulkProductions(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(bulkService.bulkUploadProductions(
                file, u.farmId(), u.userId(), u.email()));
    }

    @PostMapping(value = "/bulk/feedings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FeedBulkUploadResponse> bulkFeedings(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(bulkService.bulkUploadFeedings(
                file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping("/bulk/ingredients/{batchId}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<BatchStatusResponse> ingredientBatchStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(bulkService.bulkStatusIngredients(batchId, u.farmId()));
    }

    @GetMapping("/bulk/formulations/{batchId}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<BatchStatusResponse> formulationBatchStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(bulkService.bulkStatusFormulations(batchId, u.farmId()));
    }

    @GetMapping("/bulk/productions/{batchId}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<BatchStatusResponse> productionBatchStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(bulkService.bulkStatusProductions(batchId, u.farmId()));
    }

    @GetMapping("/bulk/feedings/{batchId}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<BatchStatusResponse> feedingBatchStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(bulkService.bulkStatusFeedings(batchId, u.farmId()));
    }

    @GetMapping("/staging/ingredients")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedFeedingIngredientResponse>> listStagedIngredients(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(bulkService.listStagedIngredients(
                u.farmId(), status, pageable(page, size))));
    }

    @GetMapping("/staging/formulations")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedFeedingFormulationResponse>> listStagedFormulations(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(bulkService.listStagedFormulations(
                u.farmId(), status, pageable(page, size))));
    }

    @GetMapping("/staging/productions")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedFeedingProductionResponse>> listStagedProductions(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(bulkService.listStagedProductions(
                u.farmId(), status, pageable(page, size))));
    }

    @GetMapping("/staging/feedings")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedFeedingEventResponse>> listStagedFeedings(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(bulkService.listStagedFeedings(
                u.farmId(), status, pageable(page, size))));
    }

    @GetMapping("/staging/ingredients/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> ingredientsStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(bulkService.ingredientsStats(u.farmId()));
    }

    @GetMapping("/staging/formulations/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> formulationsStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(bulkService.formulationsStats(u.farmId()));
    }

    @GetMapping("/staging/productions/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> productionsStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(bulkService.productionsStats(u.farmId()));
    }

    @GetMapping("/staging/feedings/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> feedingsStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(bulkService.feedingsStats(u.farmId()));
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "submittedAt"));
    }
}