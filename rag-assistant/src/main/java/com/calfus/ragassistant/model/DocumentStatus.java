package com.calfus.ragassistant.model;

/** Lifecycle of a single uploaded PDF as it goes through the ingestion pipeline. */
public enum DocumentStatus {
    PROCESSING,
    READY,
    FAILED
}
