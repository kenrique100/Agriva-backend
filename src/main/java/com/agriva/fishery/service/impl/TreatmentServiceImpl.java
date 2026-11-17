package com.agriva.fishery.service.impl;

import com.agriva.fishery.mapper.FisheryOpsMapper;
import com.agriva.fishery.model.dto.request.TreatmentRequest;
import com.agriva.fishery.model.dto.response.TreatmentResponse;
import com.agriva.fishery.model.entity.Pond;
import com.agriva.fishery.model.entity.Treatment;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.TreatmentRepository;
import com.agriva.fishery.service.TreatmentService;
import com.agriva.shared.exception.PondNotFoundException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TreatmentServiceImpl implements TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final PondRepository pondRepository;
    private final FisheryOpsMapper mapper;

    @Override
    @Transactional
    public TreatmentResponse record(UUID pondId, TreatmentRequest request,
                                    UUID farmId, UUID userId) {
        Pond pond = pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + pondId));

        Treatment saved = treatmentRepository.save(Treatment.builder()
                .farmId(farmId)
                .pondId(pond.getId())
                .medicationName(request.getMedicationName().trim())
                .dosage(request.getDosage())
                .dosageUnit(request.getDosageUnit().trim())
                .administeredAt(request.getAdministeredAt())
                .withdrawalDays(request.getWithdrawalDays())
                .administeredByUserId(userId)
                .notes(request.getNotes())
                .build());

        log.info("Treatment {} recorded for pond {} ({} {} {})",
                saved.getId(), pondId, request.getDosage(),
                request.getDosageUnit(), request.getMedicationName());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TreatmentResponse> listForPond(UUID pondId, UUID farmId, Pageable pageable) {
        pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + pondId));
        return treatmentRepository.findByPondId(pondId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(UUID pondId, UUID treatmentId, UUID farmId) {
        treatmentRepository.findByIdAndFarmId(treatmentId, farmId)
                .filter(t -> t.getPondId().equals(pondId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Treatment not found with ID: " + treatmentId));
        treatmentRepository.deleteById(treatmentId);
        log.info("Treatment {} deleted from pond {}", treatmentId, pondId);
    }
}