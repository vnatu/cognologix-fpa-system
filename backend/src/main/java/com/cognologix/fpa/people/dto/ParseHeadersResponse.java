package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.ExcelSnapshotParser;

import java.util.List;

public record ParseHeadersResponse(List<String> headers, int rowCount) {
    public static ParseHeadersResponse from(ExcelSnapshotParser.ParseHeadersResult result) {
        return new ParseHeadersResponse(result.headers(), result.rowCount());
    }
}
