package com.planner.photo_calendar.category.dto.response;

import com.planner.photo_calendar.category.Category;

public record CategoryResponse(
        Long id,
        String name,
        String color,
        Integer displayOrder,
        Boolean isPrivate
) {
    public static CategoryResponse from(Category category){
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getDisplayOrder(),
                category.getIsPrivate()
        );
    }
}
