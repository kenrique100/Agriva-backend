package com.agriva.finance.model.enums;

/**
 * Lifecycle of a staged income row.
 * <p>
 * PENDING   – received from Kafka, never attempted.
 * RETRYING  – attempted at least once, waiting for the next retry window.
 * MOVED     – passed validation and was inserted into `income`. Terminal.
 * FAILED    – exhausted retry budget. Terminal. Never appears in `income`.
 */
public enum StagedIncomeStatus {
    PENDING,
    RETRYING,
    MOVED,
    FAILED
}