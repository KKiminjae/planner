package com.planner.photo_calendar.category;

import com.planner.photo_calendar.record.DailyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            SELECT COALESCE(MAX(c.displayOrder), 0)
            FROM Category c
            WHERE c.deletedAt IS NULL
            """)
    int findMaxDisplayOrder();

    List<Category> findAllByDeletedAtIsNullOrderByDisplayOrderAsc();

    Optional<Category> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT COUNT(c)
            FROM Category c
            WHERE c.createdAt < :nextDay
                AND (c.deletedAt IS NULL OR c.deletedAt >= :startOfDay)
        """)
    long countActiveCategoriesAt(
            @Param("startOfDay")LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay
    );
}
