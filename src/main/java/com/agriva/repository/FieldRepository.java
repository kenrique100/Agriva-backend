package com.agriva.repository;

import com.agriva.model.entity.Field;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FieldRepository extends JpaRepository<Field, UUID> {
    List<Field> findByFarmId(UUID farmId);
    Optional<Field> findByIdAndFarmId(UUID id, UUID farmId);
    Optional<Field> findByFarmIdAndName(UUID farmId, String name);
    boolean existsByFarmIdAndName(UUID farmId, String name);
}