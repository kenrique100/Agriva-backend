package com.agriva.shared.config;

/**
 * Centralised cache names and TTLs.
 * Every @Cacheable/@CacheEvict uses these constants so the names never drift.
 */
public final class CacheKeys {

    public static final String FARM_BY_ID         = "farmById";
    public static final String USER_BY_ID         = "userById";
    public static final String CROPS_BY_FARM      = "cropsByFarm";
    public static final String FIELDS_BY_FARM     = "fieldsByFarm";
    public static final String ACTIVE_INVESTMENT  = "activeInvestment";
    public static final String DASHBOARD_TOTALS   = "dashboardTotals";

    private CacheKeys() {}
}