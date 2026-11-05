package com.agriva.farming.model.enums;

import lombok.Getter;

@Getter
public enum SoilType {
    SANDY("Sandy"),
    CLAY("Clay"),
    LOAMY("Loamy"),
    SILTY("Silty"),
    PEATY("Peaty"),
    CHALKY("Chalky"),
    SILT("Silt"),
    SANDY_LOAM("Sandy Loam"),
    CLAY_LOAM("Clay Loam"),
    SILT_LOAM("Silt Loam"),
    SANDY_CLAY("Sandy Clay"),
    SILTY_CLAY("Silty Clay"),
    SANDY_CLAY_LOAM("Sandy Clay Loam"),
    SILTY_CLAY_LOAM("Silty Clay Loam"),
    OTHER("Other");

    private final String displayName;

    SoilType(String displayName) {
        this.displayName = displayName;
    }

    /** Accepts both enum names and display names (case-insensitive). */
    public static SoilType fromString(String s) {
        if (s == null || s.isBlank()) return null;
        String normalized = s.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        for (SoilType v : values()) {
            if (v.name().equals(normalized) || v.displayName.toUpperCase().replace(' ', '_').equals(normalized)) {
                return v;
            }
        }
        return null;
    }
}