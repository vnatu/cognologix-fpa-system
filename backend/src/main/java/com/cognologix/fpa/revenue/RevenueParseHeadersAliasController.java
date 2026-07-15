package com.cognologix.fpa.revenue;

import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.dto.RevenueMappingDtos.ParseHeadersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Alias parse-headers for credit-notes path — same behaviour as invoices parse-headers.
 */
@RestController
@RequestMapping("/api/revenue/imports")
@RequiredArgsConstructor
@Tag(name = "Revenue", description = "Zoho Books invoice/credit-note imports and revenue summaries")
class RevenueParseHeadersAliasController {

    private final RevenueService revenueService;

    @PostMapping(value = "/credit-notes/parse-headers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse Excel column headers (credit notes) — no persist")
    public ParseHeadersResponse parseCreditNoteHeaders(@RequestPart("file") MultipartFile file) {
        var result = revenueService.parseHeaders(file);
        return new ParseHeadersResponse(result.headers(), result.rowCount());
    }
}
