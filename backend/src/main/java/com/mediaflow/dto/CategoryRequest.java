package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record CategoryRequest(
    @NotBlank @Size(max = 100) String categoryName,
    @Positive Long parentId,
    @NotNull @Min(0) @Max(100000) Integer sortOrder,
    @NotNull @Min(0) @Max(1) Integer status) {}
