package com.planner.photo_calendar.completionhistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface CompletionHistoryRepository extends JpaRepository<CompletionHistory,CompletionHistoryId> {
    long countById_RecordDateAndCategoryOwnerId(LocalDate recordDate, Long ownerId);

    long countById_RecordDate(LocalDate recordDate);
}
