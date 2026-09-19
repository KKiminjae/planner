package com.planner.photo_calendar.category.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryUpdateRequest(
        @NotBlank
        String name,

        @NotBlank
        String color,

        Boolean isPrivate
) {
}
