package com.agriva.feeding.model.entity;

import com.agriva.shared.models.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "formulation_template")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulationTemplate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** NULL = system template (shipped with Agriva, shared across farms). */
    @Column(name = "farm_id")
    private UUID farmId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_species", length = 50)
    private String targetSpecies;

    @Column(length = 500)
    private String description;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean isSystemTemplate() {
        return farmId == null;
    }
}