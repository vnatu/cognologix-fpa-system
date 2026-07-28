package com.cognologix.fpa.system.dto;

import jakarta.validation.constraints.NotBlank;

public record RestoreConfirmRequest(@NotBlank String restoreToken) {}
