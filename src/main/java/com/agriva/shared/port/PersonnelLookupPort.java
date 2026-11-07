package com.agriva.shared.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonnelLookupPort {

    Optional<PersonnelInfo> findById(UUID personnelId);

    Optional<PersonnelInfo> findByIdAndFarmId(UUID personnelId, UUID farmId);

    Optional<PersonnelInfo> findByUserId(UUID userId);

    List<PersonnelInfo> findAllByFarmId(UUID farmId);

    record PersonnelInfo(
            UUID id,
            UUID userId,
            UUID farmId,
            String name,
            String status,
            String department,
            String email
    ) {}
}