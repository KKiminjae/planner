package com.planner.photo_calendar.record;

import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.time.LocalDate;
import java.util.List;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    Optional<DailyRecord> findByIdAndCategoryOwnerId(Long id, Long ownerId);
    List<DailyRecord> findAllByRecordDateAndCategoryOwnerIdOrderByRecordTimeAsc(LocalDate date, Long ownerId);
    long countByRecordDateAndCategoryOwnerId(LocalDate date, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DailyRecord r where r.id = :id and r.category.ownerId = :ownerId")
    Optional<DailyRecord> findOwnedByIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DailyRecord r where r.id = :id")
    Optional<DailyRecord> findByIdForUpdate(@Param("id") Long id);

    boolean existsByImageKey(String imageKey);

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
