package com.cognologix.fpa.general.dto;

import com.cognologix.fpa.general.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole role) {}
