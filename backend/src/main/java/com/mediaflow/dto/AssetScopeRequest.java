package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record AssetScopeRequest(
    @NotNull @Pattern(regexp = "LIBRARY|RESTRICTED") String accessScope,
    @NotNull @Min(0) Integer version) {}
