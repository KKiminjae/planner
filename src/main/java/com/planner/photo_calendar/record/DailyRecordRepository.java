package com.planner.photo_calendar.record;

import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    boolean existsByCategoryIdAndRecordDate(
            Long categoryId,
            LocalDate recordDate
    );

    List<DailyRecord> findAllByRecordDateOrderByRecordTimeAsc(LocalDate recordDate);

    List<DailyRecord> findAllByCategoryId(Long categoryId);

    List<DailyRecord> findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
            Long id, LocalDate start, LocalDate end
    );

    long countByRecordDate(LocalDate date);
}
