package com.planner.photo_calendar.completionhistory;

import com.planner.photo_calendar.category.Category;
import jakarta.persistence.*;

@Entity
@Table(name = "completion_history")
public class CompletionHistory {
    @EmbeddedId
    private CompletionHistoryId id;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected CompletionHistory(){
    }

    public CompletionHistory(Category category, CompletionHistoryId id) {
        this.category = category;
        this.id = id;
    }
}
