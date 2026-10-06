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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id and c.ownerId = :ownerId and c.deletedAt is null")
    Optional<Category> findActiveOwnedByIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);


    @Query("select coalesce(max(c.displayOrder), 0) from Category c where c.ownerId = :ownerId and c.deletedAt is null")
    int findMaxDisplayOrderByOwnerId(@Param("ownerId") Long ownerId);

    List<Category> findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long ownerId);
    Optional<Category> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    default List<Category> findActiveCategoriesByOwnerAt(Long ownerId, LocalDateTime startOfDay, LocalDateTime nextDay) {
        return findCategoriesForDate(ownerId, startOfDay, nextDay,
                com.planner.photo_calendar.common.time.ApplicationTime.calendarDate(startOfDay));
    }

    default long countActiveCategoriesByOwnerAt(Long ownerId, LocalDateTime startOfDay, LocalDateTime nextDay) {
        return countCategoriesForDate(ownerId, startOfDay, nextDay,
                com.planner.photo_calendar.common.time.ApplicationTime.calendarDate(startOfDay));
    }

    @Query("""
            select c from Category c where c.ownerId = :ownerId
              and (c.createdAt < :nextDay
                   or exists (select r.id from DailyRecord r where r.category = c and r.recordDate = :recordDate)
                   or exists (select h.id from CompletionHistory h where h.category = c and h.id.recordDate = :recordDate))
              and (c.deletedAt is null or c.deletedAt >= :startOfDay)
            order by c.displayOrder, c.id
            """)
    List<Category> findCategoriesForDate(@Param("ownerId") Long ownerId,
                                         @Param("startOfDay") LocalDateTime startOfDay,
                                         @Param("nextDay") LocalDateTime nextDay,
                                         @Param("recordDate") LocalDate recordDate);

    @Query("""
            select count(c) from Category c where c.ownerId = :ownerId
              and (c.createdAt < :nextDay
                   or exists (select r.id from DailyRecord r where r.category = c and r.recordDate = :recordDate)
                   or exists (select h.id from CompletionHistory h where h.category = c and h.id.recordDate = :recordDate))
              and (c.deletedAt is null or c.deletedAt >= :startOfDay)
            """)
    long countCategoriesForDate(@Param("ownerId") Long ownerId,
                                @Param("startOfDay") LocalDateTime startOfDay,
                                @Param("nextDay") LocalDateTime nextDay,
                                @Param("recordDate") LocalDate recordDate);

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
