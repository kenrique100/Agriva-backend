package com.agriva.feeding.calculator;

import com.agriva.shared.exception.BusinessException;

import java.util.List;
import java.util.Locale;

public final class SpeciesKey {

    public static final String CATFISH = "CATFISH";
    public static final String TILAPIA = "TILAPIA";

    private static final List<String> SUPPORTED = List.of(CATFISH, TILAPIA);

    private SpeciesKey() {}

    /** Returns the canonical key for a raw user-supplied species string. */
    public static String of(String species) {
        if (species == null || species.isBlank()) {
            throw new BusinessException("Species is required");
        }
        String upper = species.trim().toUpperCase(Locale.ROOT);
        for (String key : SUPPORTED) {
            if (upper.contains(key)) return key;
        }
        throw new BusinessException(
                "Species '" + species + "' is not supported. Supported: " + SUPPORTED);
    }

    /** e.g. "TILAPIA" -> "AGRIVA_DEFAULT_TILAPIA_GROWER". */
    public static String defaultTemplateNameFor(String species) {
        return "AGRIVA_DEFAULT_" + of(species) + "_GROWER";
    }

    public static List<String> supported() {
        return SUPPORTED;
    }
}