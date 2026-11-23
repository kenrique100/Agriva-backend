package com.agriva.feeding.utils;

import com.agriva.feeding.config.FeedCpProperties;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngredientCrudeProteinResolver {

    private final IngredientRepository ingredientRepo;
    private final FeedCpProperties cpProperties;

    /** Effective CP% keyed by ingredient id, for the given farm. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> resolveByIngredientId(UUID farmId) {
        Map<String, BigDecimal> cpByName = cpProperties.asNameMap();
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        for (Ingredient ing : ingredientRepo.findAllByFarmIdLive(farmId)) {
            BigDecimal cp = effectiveCp(ing, cpByName);
            if (cp != null) out.put(ing.getId(), cp);
        }
        return out;
    }

    /** Effective CP% keyed by ingredient display name, for the given farm. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> resolveByName(UUID farmId) {
        Map<String, BigDecimal> cpByName = cpProperties.asNameMap();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Ingredient ing : ingredientRepo.findAllByFarmIdLive(farmId)) {
            BigDecimal cp = effectiveCp(ing, cpByName);
            if (cp != null) out.put(ing.getName(), cp);
        }
        return out;
    }

    /** Config CP for an arbitrary ingredient name, independent of farm ingredients. */
    public BigDecimal cpForName(String name) {
        if (name == null) return null;
        return cpProperties.asNameMap().get(lower(name));
    }

    private static BigDecimal effectiveCp(Ingredient ing, Map<String, BigDecimal> cpByName) {
        if (ing.getCrudeProteinPct() != null) return ing.getCrudeProteinPct();
        return cpByName.get(lower(ing.getName()));
    }

    private static String lower(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}