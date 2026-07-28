package com.cognologix.fpa.general;

import java.util.List;

/**
 * Tabular backup payload for one Excel file in a full system backup (ADR-044 Tier 2).
 * Modules supply {@code headers} and {@code rows}; the orchestrator writes the workbook.
 */
public record BackupSheet(String fileName, String[] headers, List<String[]> rows) {}
