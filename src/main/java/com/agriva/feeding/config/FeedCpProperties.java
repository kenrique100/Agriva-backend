package com.agriva.feeding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reference crude-protein values per ingredient, sourced from
 * application.properties (agriva.feeding.cp.*). Change the property,
 * restart — no migration needed. User-set values on
 * ingredient.crude_protein_pct still override these defaults.
 */
@Data
@Component
@ConfigurationProperties(prefix = "agriva.feeding.cp")
public class FeedCpProperties {

    private BigDecimal soyaBeans           = new BigDecimal("44.0");
    private BigDecimal groundnuts          = new BigDecimal("45.0");
    private BigDecimal bloodMeal           = new BigDecimal("80.0");
    private BigDecimal fishMeal            = new BigDecimal("65.0");
    private BigDecimal maize               = new BigDecimal("9.0");
    private BigDecimal cassava             = new BigDecimal("2.0");
    private BigDecimal wheatBran           = new BigDecimal("0");
    private BigDecimal diphosphateCalcium  = new BigDecimal("0");
    private BigDecimal boneMeal            = new BigDecimal("0");
    private BigDecimal marineShellFlour    = new BigDecimal("0");
    private BigDecimal salt                = new BigDecimal("0");
    private BigDecimal vitaminC            = new BigDecimal("0");
    private BigDecimal premix              = new BigDecimal("0");
    private BigDecimal palmOil             = new BigDecimal("0");
    private BigDecimal antiToxin           = new BigDecimal("0");

    /**
     * Returns a name → CP lookup keyed by lowercase trimmed ingredient name.
     * Includes common spelling variants so users' ingredient names match.
     */
    public Map<String, BigDecimal> asNameMap() {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        put(out, "soya beans", soyaBeans);
        put(out, "soybeans", soyaBeans);
        put(out, "soya bean", soyaBeans);
        put(out, "groundnuts", groundnuts);
        put(out, "groundnut", groundnuts);
        put(out, "blood meal", bloodMeal);
        put(out, "fish meal", fishMeal);
        put(out, "maize", maize);
        put(out, "cassava", cassava);
        put(out, "wheat bran", wheatBran);
        put(out, "diphosphate calcium", diphosphateCalcium);
        put(out, "bone meal", boneMeal);
        put(out, "marine shell flour", marineShellFlour);
        put(out, "salt", salt);
        put(out, "vitamin c", vitaminC);
        put(out, "premix", premix);
        put(out, "palm oil", palmOil);
        put(out, "anti-toxin", antiToxin);
        put(out, "antitoxin", antiToxin);
        return out;
    }

    private static void put(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) map.put(key.trim().toLowerCase(Locale.ROOT), value);
    }
}