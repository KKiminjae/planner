package com.planner.photo_calendar.record;

import com.planner.photo_calendar.category.Category;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_records_category_date",
                        columnNames = {"category_id", "record_date"}
                )
        }
)
@Getter
public class DailyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "record_time", nullable = false)
    private LocalTime recordTime;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String memo;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DailyRecord() {
    }

    public DailyRecord(Category category, LocalDate recordDate, LocalTime recordTime, String memo, String imageKey) {
        this.category = category;
        this.recordDate = recordDate;
        this.recordTime = recordTime;
        this.memo = memo;
        this.imageKey = imageKey;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(LocalTime recordTime, String memo){
        this.recordTime = recordTime;
        this.memo = memo;
        this.updatedAt = LocalDateTime.now();
    }
}
