package com.cognologix.fpa.system;

import com.cognologix.fpa.general.AdminOnly;
import com.cognologix.fpa.system.dto.RestoreConfirmRequest;
import com.cognologix.fpa.system.dto.RestoreConfirmResponse;
import com.cognologix.fpa.system.dto.RestoreDryRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "System Backup & Restore", description = "Full replace backup/restore")
public class SystemBackupController {

    private final SystemBackupService systemBackupService;

    @AdminOnly
    @GetMapping("/backup")
    @Operation(summary = "Download full system backup ZIP (Admin only)")
    public ResponseEntity<byte[]> downloadBackup() {
        var backup = systemBackupService.createBackup();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + backup.filename() + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(backup.content());
    }

    @AdminOnly
    @GetMapping("/backup/meta")
    @Operation(summary = "Last backup timestamp from general_config")
    public Map<String, String> backupMeta() {
        String last = systemBackupService.lastBackupAt();
        return Map.of("lastBackupAt", last != null ? last : "");
    }

    @AdminOnly
    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Dry-run restore — returns summary + token; does not mutate data")
    public RestoreDryRunResponse restoreDryRun(
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        return systemBackupService.prepareRestore(file, actor(auth));
    }

    @AdminOnly
    @PostMapping("/restore/confirm")
    @Operation(summary = "Confirm restore using token from dry-run — destructive full replace")
    public RestoreConfirmResponse restoreConfirm(
            @Valid @RequestBody RestoreConfirmRequest request,
            Authentication auth) {
        return systemBackupService.confirmRestore(request.restoreToken(), actor(auth));
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }
}
