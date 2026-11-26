package com.agriva.personnel.repository;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.enums.PersonnelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonnelRepository extends JpaRepository<Personnel, UUID> {

    @Query("SELECT p FROM Personnel p WHERE p.farmId = :farmId AND p.status = :status " +
            "AND p.deletedAt IS NULL")
    Page<Personnel> findByFarmIdAndStatusLive(@Param("farmId") UUID farmId,
                                              @Param("status") PersonnelStatus status,
                                              Pageable p);

    @Query("SELECT p FROM Personnel p WHERE p.status = :status AND p.deletedAt IS NULL")
    List<Personnel> findByStatusLive(@Param("status") PersonnelStatus status);


    boolean existsByFarmIdAndNationalId(UUID farmId, String nationalId);
    boolean existsByFarmIdAndNationalIdAndIdNot(UUID farmId, String nationalId, UUID id);
    boolean existsByFarmIdAndProfilePicturePath(UUID farmId, String profilePicturePath);
    boolean existsByFarmIdAndProfilePictureThumbnailPath(UUID farmId,
                                                         String profilePictureThumbnailPath);

    long countByFarmId(UUID farmId);

    List<Personnel> findByStatusExpirationBeforeAndStatusIn(LocalDateTime now,
                                                            List<PersonnelStatus> statuses);
    List<Personnel> findByStatusAndTerminationTimestampBefore(PersonnelStatus status,
                                                              LocalDateTime cutoff);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM Personnel p WHERE p.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);

    @Query("""
        SELECT p.profilePicturePath FROM Personnel p
        WHERE p.farmId = :farmId AND p.profilePicturePath IS NOT NULL
    """)
    List<String> findProfilePicturePathsByFarmId(@Param("farmId") UUID farmId);

    @Query("""
        SELECT p.profilePictureThumbnailPath FROM Personnel p
        WHERE p.farmId = :farmId AND p.profilePictureThumbnailPath IS NOT NULL
    """)
    List<String> findProfilePictureThumbnailPathsByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT p FROM Personnel p WHERE p.farmId = :farmId AND p.deletedAt IS NULL")
    Page<Personnel> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT p FROM Personnel p WHERE p.id = :id AND p.farmId = :farmId "
            + "AND p.deletedAt IS NULL")
    Optional<Personnel> findByIdAndFarmIdLive(@Param("id") UUID id,
                                              @Param("farmId") UUID farmId);

    @Query("SELECT p FROM Personnel p WHERE p.userId = :userId AND p.deletedAt IS NULL")
    Optional<Personnel> findByUserIdLive(@Param("userId") UUID userId);

    Optional<Personnel> findByUserId(UUID userId);

    @Query("""
            SELECT p FROM Personnel p
            WHERE p.farmId = :farmId
              AND p.deletedAt IS NULL
              AND (p.isFarmOwner IS NULL OR p.isFarmOwner = FALSE)
            """)
    Page<Personnel> findStaffByFarmIdLive(@Param("farmId") UUID farmId,
                                          Pageable pageable);

    @Query("""
            SELECT p FROM Personnel p
            WHERE p.farmId = :farmId
              AND p.status = :status
              AND p.deletedAt IS NULL
              AND (p.isFarmOwner IS NULL OR p.isFarmOwner = FALSE)
            """)
    Page<Personnel> findStaffByFarmIdAndStatusLive(@Param("farmId") UUID farmId,
                                                   @Param("status") PersonnelStatus status,
                                                   Pageable pageable);

    @Query("""
            SELECT p FROM Personnel p
            WHERE p.farmId = :farmId
              AND p.deletedAt IS NULL
              AND (p.isFarmOwner IS NULL OR p.isFarmOwner = FALSE)
            """)
    List<Personnel> findAllStaffByFarmIdLive(@Param("farmId") UUID farmId);

    @Modifying
    @Query("""
            UPDATE Personnel p
               SET p.totalHoursWorkedLast30Days = 0,
                   p.totalProductiveDays = 0,
                   p.workingDaysCount = 0,
                   p.currentPeriodStartDate = :now
             WHERE p.deletedAt IS NULL
               AND p.status = com.agriva.personnel.models.enums.PersonnelStatus.ACTIVE
               AND (p.isFarmOwner IS NULL OR p.isFarmOwner = FALSE)
            """)
    int bulkResetProductivity(@Param("now") LocalDate now);

    void deleteByPersonnelId(UUID personnelId);
}