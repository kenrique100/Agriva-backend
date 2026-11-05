package com.agriva.personnel.repository;

import com.agriva.personnel.models.entity.PersonnelStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonnelStatusHistoryRepository extends JpaRepository<PersonnelStatusHistory, UUID> {

    List<PersonnelStatusHistory> findByPersonnelIdOrderByStartTimestampDesc(UUID personnelId);

    Optional<PersonnelStatusHistory> findByPersonnelIdAndEndTimestampIsNull(UUID personnelId);

    void deleteByPersonnelId(UUID personnelId);
}