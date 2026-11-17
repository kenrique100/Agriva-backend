package com.agriva.fishery.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per species in a pond. A pond can hold multiple species
 * (e.g. tilapia + catfish polyculture) — each gets its own row.
 * <p>
 * Farm scoping is guaranteed by first resolving the parent Pond via
 * {@code PondRepository.findByIdAndFarmIdLive(...)} before touching stock.
 * There is no {@code farmId} column on this table by design.
 */
@Entity
@Table(name = "pond_stock")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PondStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pond_id", nullable = false)
    private UUID pondId;

    @Column(nullable = false, length = 50)
    private String species;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "average_weight_g", precision = 12, scale = 2)
    private BigDecimal averageWeightG;

    @Column(length = 30)
    private String stage;

    @Column(name = "stocked_at")
    private LocalDate stockedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}