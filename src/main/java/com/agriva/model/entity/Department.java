package com.agriva.model.entity;

public enum Department {
    ADMINISTRATION("Administration"),
    FISHERY("Fishery"),
    POULTRY("Poultry"),
    RABBITRY("Rabbitry"),
    CONSTRUCTION("Construction"),
    CROPS("Crops"),
    LIVESTOCK("Livestock"),
    FARM_MANAGEMENT("Farm Management"),
    DAIRY("Dairy"),
    AGRO_FORESTRY("Agro Forestry"),
    IRRIGATION("Irrigation"),
    AGRICULTURAL_ENGINEERING("Agricultural Engineering"),
    FOOD_PROCESSING("Food Processing");

    private final String displayName;

    Department(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }
}