package com.agriva.repository;

import com.agriva.model.entity.Farm;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FarmRepository extends JpaRepository<Farm, UUID> {

    boolean existsByName(String name);

    @Query("SELECT f FROM Farm f WHERE f.id = :id AND f.deletedAt IS NULL")
    Optional<Farm> findByIdLive(@Param("id") UUID id);

    @Query("SELECT f FROM Farm f WHERE f.deletedAt IS NULL")
    List<Farm> findAllLive();

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Farm f " +
            "WHERE LOWER(f.name) = LOWER(:name) AND f.deletedAt IS NULL")
    boolean existsByNameLive(@Param("name") String name);
}