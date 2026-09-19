package com.planner.photo_calendar.category.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryCreateRequest(

        @NotBlank
        String name,

        @NotBlank
        String color,

        boolean isPrivate
) {
}
