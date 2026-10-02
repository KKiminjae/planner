package com.planner.photo_calendar.category.dto.response;

import com.planner.photo_calendar.category.Category;

public record DailyCategoryResponse(Long id, String name, String color, Integer displayOrder,
                                    Boolean isPrivate, boolean isDeleted) {
    public static DailyCategoryResponse from(Category category) {
        return new DailyCategoryResponse(category.getId(), category.getName(), category.getColor(),
                category.getDisplayOrder(), category.getIsPrivate(), category.getDeletedAt() != null);
    }
}
