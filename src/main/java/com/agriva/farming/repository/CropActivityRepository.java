package com.agriva.farming.repository;
import com.agriva.farming.model.entity.CropActivity;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface CropActivityRepository extends JpaRepository<CropActivity, UUID> {
    @EntityGraph(attributePaths = {"cropCycle", "cropCycle.crop", "cropCycle.field"})
    List<CropActivity> findByCropCycleIdOrderByActivityDateDesc(UUID cropCycleId);
}