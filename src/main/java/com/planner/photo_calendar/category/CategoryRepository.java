package com.planner.photo_calendar.category;

import com.planner.photo_calendar.record.DailyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
