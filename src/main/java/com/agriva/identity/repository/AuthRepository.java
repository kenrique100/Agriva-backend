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

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailLive(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.deletedAt IS NULL")
    Optional<User> findByUsernameLive(@Param("username") String username);

    /**
     * Used by AuthServiceImpl.login(...). Placeholder names :email and
     * :username MUST match the @Param names exactly. Previous code had
     * the query text using :e / :u while the annotations said
     * @Param("email") / @Param("username"), so Hibernate raised
     * "No argument for named parameter ':e'".
     */
    @Query("""
        SELECT u FROM User u
        WHERE (u.email = :email OR u.username = :username)
          AND u.deletedAt IS NULL
    """)
    Optional<User> findByEmailOrUsernameLive(
            @Param("email") String email,
            @Param("username") String username);

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
            "WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmailLive(@Param("email") String email);

    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmailOrUsername(String email, String username);

    boolean existsByEmail(String email);
    boolean existsByFarmIdAndRole(UUID farmId, String role);

    List<User> findByFarmId(UUID farmId);
    List<User> findByRole(String role);
}