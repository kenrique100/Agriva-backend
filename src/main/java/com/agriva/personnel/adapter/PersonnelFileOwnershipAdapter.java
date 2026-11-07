package com.agriva.personnel.adapter;

import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.shared.port.FileOwnershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PersonnelFileOwnershipAdapter implements FileOwnershipPort {

    private final PersonnelRepository repo;

    @Override
    public boolean isOwnedByFarm(String fileName, UUID farmId) {
        return repo.existsByFarmIdAndProfilePicturePath(farmId, fileName)
                || repo.existsByFarmIdAndProfilePictureThumbnailPath(farmId, fileName);
    }
}