package com.agriva.repository;

import com.agriva.model.entity.Personnel;
import com.agriva.model.entity.enums.PersonnelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonnelRepository extends JpaRepository<Personnel, UUID> {

    Page<Personnel> findByFarmId(UUID farmId, Pageable pageable);

    Page<Personnel> findByFarmIdAndStatus(UUID farmId, PersonnelStatus status, Pageable pageable);

    Optional<Personnel> findByIdAndFarmId(UUID id, UUID farmId);

    /** Required by the 1:1 Personnel↔User model. */
    Optional<Personnel> findByUserId(UUID userId);

    boolean existsByFarmIdAndNationalId(UUID farmId, String nationalId);

    boolean existsByFarmIdAndNationalIdAndIdNot(UUID farmId, String nationalId, UUID id);

    boolean existsByFarmIdAndProfilePicturePath(UUID farmId, String profilePicturePath);

    boolean existsByFarmIdAndProfilePictureThumbnailPath(UUID farmId, String profilePictureThumbnailPath);

    long countByFarmId(UUID farmId);

    List<Personnel> findByStatusExpirationBeforeAndStatusIn(
            LocalDateTime now, List<PersonnelStatus> statuses);

    List<Personnel> findByStatusAndTerminationTimestampBefore(
            PersonnelStatus status, LocalDateTime cutoff);

    List<Personnel> findByFarmIdAndStatus(UUID farmId, PersonnelStatus status);

    List<Personnel> findByStatus(PersonnelStatus status);

    void deleteByUserId(UUID userId);
}