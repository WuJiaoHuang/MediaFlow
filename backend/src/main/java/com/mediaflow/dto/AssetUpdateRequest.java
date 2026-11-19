package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record AssetUpdateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotNull @Size(max = 2000) String description,
    @Positive Long categoryId,
    @NotNull @Min(0) Integer version) {}
