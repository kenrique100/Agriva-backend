package com.agriva.poultry.model.enums;

import lombok.Getter;

@Getter
public enum PoultrySpecies {
    BROILER("Broiler"),
    LAYER("Layer"),
    COCKEREL("Cockerel"),
    TURKEY("Turkey"),
    DUCK("Duck"),
    OTHER("Other");

    private final String displayName;

    PoultrySpecies(String displayName) {
        this.displayName = displayName;
    }

    public static PoultrySpecies fromString(String s) {
        if (s == null || s.isBlank()) return null;
        String normalized = s.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        for (PoultrySpecies v : values()) {
            if (v.name().equals(normalized)) return v;
        }
        return null;
    }
}