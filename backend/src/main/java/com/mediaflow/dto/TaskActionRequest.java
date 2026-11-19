package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record TaskActionRequest(
    @NotNull @Min(0) Integer version, @Size(max = 2000) String reason) {}
