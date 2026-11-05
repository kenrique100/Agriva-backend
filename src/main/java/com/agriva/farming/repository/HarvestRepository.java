package com.agriva.farming.repository;

import com.agriva.farming.model.entity.Harvest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface HarvestRepository extends JpaRepository<Harvest, UUID> {
    List<Harvest> findByCropCycleId(UUID cropCycleId);
}