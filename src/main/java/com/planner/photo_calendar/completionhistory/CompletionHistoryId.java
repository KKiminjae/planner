package com.planner.photo_calendar.completionhistory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class CompletionHistoryId implements Serializable {

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "record_date")
    private LocalDate recordDate;

    protected CompletionHistoryId() {
    }

    public CompletionHistoryId(Long categoryId, LocalDate recordDate) {
        this.categoryId = categoryId;
        this.recordDate = recordDate;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(!(o instanceof CompletionHistoryId that)) {
            return false;
        }
        return Objects.equals(categoryId, that.categoryId)
                && Objects.equals(recordDate, that.recordDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, recordDate);
    }
}
