package com.mediaflow.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public record TaskRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String description,
    @NotNull @Positive Long assigneeId,
    @NotNull @Positive Long reviewerId,
    @NotNull @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
    LocalDateTime dueAt,
    @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> assetIds,
    @Min(0) Integer version) {}
