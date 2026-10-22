package com.agriva.service.impl;

import com.agriva.exception.*;
import com.agriva.mapper.PersonnelMapper;
import com.agriva.mapper.SalaryMapper;
import com.agriva.mapper.TaskMapper;
import com.agriva.model.dto.personnel.*;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.repository.*;
import com.agriva.security.RoleConstants;
import com.agriva.service.FileStorageService;
import com.agriva.service.PersonnelService;
import com.agriva.utils.PersonnelValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonnelServiceImpl implements PersonnelService {

    private final PersonnelRepository personnelRepository;
    private final PersonnelStatusHistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final SalaryPaymentRepository salaryRepository;
    private final AuthRepository authRepository;
    private final PersonnelValidator validator;
    private final PersonnelMapper mapper;
    private final TaskMapper taskMapper;
    private final SalaryMapper salaryMapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<PersonnelResponse> list(UUID farmId, PersonnelStatus status, Pageable pageable) {
        Page<Personnel> page = status == null
                ? personnelRepository.findByFarmId(farmId, pageable)
                : personnelRepository.findByFarmIdAndStatus(farmId, status, pageable);
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonnelResponse get(UUID id, UUID farmId) {
        return mapper.toResponse(load(id, farmId));
    }

    @Override
    @Transactional(readOnly = true)
    public PersonnelProfileResponse getProfile(UUID id, UUID farmId) {
        return buildProfile(load(id, farmId));
    }

    @Override
    @Transactional(readOnly = true)
    public PersonnelProfileResponse getOwnProfile(UUID userId) {
        Personnel p = personnelRepository.findByUserId(userId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "No personnel record linked to this user"));
        return buildProfile(p);
    }

    /**
     * Called from within @Transactional(readOnly=true) outer methods above,
     * so the session stays open across the two repository queries AND the
     * subsequent TaskMapper / SalaryMapper calls that touch the lazy
     * .personnel proxy on Task / SalaryPayment entities.
     */
    private PersonnelProfileResponse buildProfile(Personnel p) {
        List<PersonnelStatusHistoryResponse> history =
                historyRepository.findByPersonnelIdOrderByStartTimestampDesc(p.getId())
                        .stream().map(mapper::toHistoryResponse).toList();
        return PersonnelProfileResponse.builder()
                .personnel(mapper.toResponse(p))
                .statusHistory(history)
                .tasks(taskRepository.findByPersonnelId(p.getId())
                        .stream().map(taskMapper::toResponse).toList())
                .salaryPayments(salaryRepository.findByPersonnelId(p.getId())
                        .stream().map(salaryMapper::toResponse).toList())
                .build();
    }

    @Override
    @Transactional
    public PersonnelResponse update(UUID id, PersonnelUpdateRequest req, UUID farmId) {
        Personnel p = load(id, farmId);
        validator.validateUpdate(p, req);

        if (req.getName() != null && !req.getName().isBlank()) p.setName(req.getName());
        if (req.getGender() != null) p.setGender(req.getGender());
        if (req.getDateOfBirth() != null) p.setDateOfBirth(req.getDateOfBirth());
        if (req.getEmail() != null) p.setEmail(req.getEmail());
        if (req.getNationalId() != null) p.setNationalId(req.getNationalId());
        if (req.getPhoneNumber() != null) p.setPhoneNumber(req.getPhoneNumber());
        if (req.getDepartment() != null) p.setDepartment(req.getDepartment());
        if (req.getDateOfEmployment() != null) p.setDateOfEmployment(req.getDateOfEmployment());

        return mapper.toResponse(personnelRepository.save(p));
    }

    @Override
    @Transactional
    public PersonnelResponse changeStatus(UUID id, PersonnelStatusUpdateRequest req, UUID farmId) {
        Personnel p = load(id, farmId);
        validator.validateStatusTransition(p, req.getStatus());
        validator.validateStatusPayload(req);

        closeCurrentHistory(p);

        PersonnelStatus newStatus = req.getStatus();
        p.setStatusChangeTimestamp(LocalDateTime.now());
        p.setStatusExpiration(null);
        p.setSuspensionDuration(null);
        p.setTerminationTimestamp(null);

        PersonnelStatusHistory h = PersonnelStatusHistory.builder()
                .personnel(p)
                .status(newStatus)
                .startTimestamp(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        switch (newStatus) {
            case ON_LEAVE -> {
                LocalDateTime ret = req.getExpectedReturnDate().atStartOfDay();
                p.setStatusExpiration(ret);
                h.setExpectedEndTimestamp(ret);
                h.setAllocatedDuration(Duration.between(
                        req.getLeaveStartDate().atStartOfDay(), ret));
            }
            case SUSPENDED -> {
                LocalDateTime end = LocalDateTime.now().plus(req.getSuspensionDuration());
                p.setSuspensionDuration(req.getSuspensionDuration());
                p.setStatusExpiration(end);
                h.setAllocatedDuration(req.getSuspensionDuration());
                h.setExpectedEndTimestamp(end);
            }
            case TERMINATED -> p.setTerminationTimestamp(LocalDateTime.now());
            default -> { }
        }
        p.setStatus(newStatus);
        personnelRepository.save(p);
        historyRepository.save(h);
        log.info("Personnel {} status -> {}", id, newStatus);
        return mapper.toResponse(p);
    }

    private void closeCurrentHistory(Personnel p) {
        historyRepository.findByPersonnelIdAndEndTimestampIsNull(p.getId())
                .ifPresent(h -> {
                    LocalDateTime now = LocalDateTime.now();
                    h.setEndTimestamp(now);
                    h.setActualDuration(Duration.between(h.getStartTimestamp(), now));
                    historyRepository.save(h);
                });
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID farmId) {
        Personnel p = load(id, farmId);
        User user = authRepository.findById(p.getUserId())
                .orElseThrow(() -> new BusinessException("Linked user not found"));

        if (RoleConstants.MASTER.equals(user.getRole())) {
            throw new BusinessException(
                    "Cannot delete MASTER via personnel endpoint. Delete the farm or the account instead.");
        }

        taskRepository.deleteByPersonnelId(p.getId());
        salaryRepository.deleteByPersonnelId(p.getId());
        authRepository.delete(user);
        log.info("Personnel {} and linked user {} deleted from farm {}",
                id, user.getId(), farmId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonnelStatusHistoryResponse> getStatusHistory(UUID id, UUID farmId) {
        load(id, farmId);
        return historyRepository.findByPersonnelIdOrderByStartTimestampDesc(id)
                .stream().map(mapper::toHistoryResponse).toList();
    }

    @Override
    @Transactional
    public PersonnelResponse uploadPicture(UUID id, UUID farmId, MultipartFile file) {
        Personnel p = load(id, farmId);
        if (p.getProfilePicturePath() != null) {
            fileStorageService.delete(p.getProfilePicturePath());
        }
        String stored = fileStorageService.storeFile(file);
        p.setProfilePicturePath(stored);
        return mapper.toResponse(personnelRepository.save(p));
    }

    private Personnel load(UUID id, UUID farmId) {
        return personnelRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + id));
    }

    @Override
    @Transactional
    public void deletePicture(UUID id, UUID farmId) {
        Personnel p = load(id, farmId);
        if (p.getProfilePicturePath() != null) {
            fileStorageService.delete(p.getProfilePicturePath());
            p.setProfilePicturePath(null);
            personnelRepository.save(p);
            log.info("Personnel {} profile picture deleted", id);
        }
    }
}