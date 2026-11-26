package com.agriva.personnel.adapter;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.shared.port.PersonnelLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PersonnelLookupAdapter implements PersonnelLookupPort {

    private final PersonnelRepository repo;

    @Override
    public Optional<PersonnelInfo> findById(UUID personnelId) {
        return repo.findById(personnelId).map(this::toInfo);
    }

    @Override
    public Optional<PersonnelInfo> findByIdAndFarmId(UUID personnelId, UUID farmId) {
        return repo.findByIdAndFarmIdLive(personnelId, farmId).map(this::toInfo);
    }

    @Override
    public Optional<PersonnelInfo> findByUserId(UUID userId) {
        return repo.findByUserIdLive(userId).map(this::toInfo);
    }

    @Override
    public List<PersonnelInfo> findAllByFarmId(UUID farmId) {
        return repo.findAllStaffByFarmIdLive(farmId).stream()
                .map(this::toInfo)
                .toList();
    }

    private PersonnelInfo toInfo(Personnel p) {
        return new PersonnelInfo(
                p.getId(),
                p.getUserId(),
                p.getFarmId(),
                p.getName(),
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getDepartment() != null ? p.getDepartment().name() : null,
                p.getEmail(),
                Boolean.TRUE.equals(p.getIsFarmOwner())
        );
    }
}