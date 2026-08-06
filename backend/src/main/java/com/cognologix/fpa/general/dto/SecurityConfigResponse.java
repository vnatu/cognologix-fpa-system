package com.cognologix.fpa.general.dto;

public record SecurityConfigResponse(Integer jwtExpiryHours, Integer inactivityTimeoutMinutes) {}
