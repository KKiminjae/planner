package com.planner.photo_calendar.category.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CategoryReorderRequest (
        @NotEmpty
        List<@NotNull Long> categoryIds
){
}
