package com.agriva.personnel.service;

import com.agriva.personnel.models.dto.response.PersonnelProfileResponse;
import com.agriva.personnel.models.dto.request.PersonnelStatusUpdateRequest;
import com.agriva.personnel.models.dto.request.PersonnelUpdateRequest;
import com.agriva.personnel.models.dto.response.PersonnelResponse;
import com.agriva.personnel.models.dto.response.PersonnelStatusHistoryResponse;
import com.agriva.personnel.models.enums.PersonnelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PersonnelService {

    Page<PersonnelResponse> list(UUID farmId, PersonnelStatus status, Pageable pageable);

    PersonnelResponse get(UUID id, UUID farmId);

    PersonnelProfileResponse getProfile(UUID id, UUID farmId);

    PersonnelResponse update(UUID id, PersonnelUpdateRequest request, UUID farmId);

    PersonnelResponse changeStatus(UUID id, PersonnelStatusUpdateRequest request, UUID farmId);

    void delete(UUID id, UUID farmId);

    List<PersonnelStatusHistoryResponse> getStatusHistory(UUID id, UUID farmId);

    PersonnelResponse uploadPicture(UUID id, UUID farmId, MultipartFile file);

    PersonnelProfileResponse getOwnProfile(UUID userId);

    void deletePicture(UUID id, UUID farmId);
}