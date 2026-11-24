package com.agriva.identity.repository;

import com.agriva.identity.models.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
    Optional<User> findByEmailLive(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username) AND u.deletedAt IS NULL")
    Optional<User> findByUsernameLive(@Param("username") String username);

    @Query("""
        SELECT u FROM User u
        WHERE (LOWER(u.email) = LOWER(:identity) OR LOWER(u.username) = LOWER(:identity))
          AND u.deletedAt IS NULL
    """)
    Optional<User> findByEmailOrUsernameLive(@Param("identity") String identity);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findByIdLive(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.farmId = :farmId AND u.deletedAt IS NULL")
    List<User> findByFarmIdLive(@Param("farmId") UUID farmId);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.deletedAt IS NULL")
    List<User> findByRoleLive(@Param("role") String role);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL")
    List<User> findAllLive();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.deletedAt IS NULL")
    long countByRoleLive(@Param("role") String role);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u " +
            "WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
    boolean existsByEmailLive(@Param("email") String email);

    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByFarmIdAndRole(UUID farmId, String role);
    List<User> findByFarmId(UUID farmId);
    List<User> findByRole(String role);
}