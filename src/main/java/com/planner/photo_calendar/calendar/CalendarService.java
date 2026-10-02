package com.planner.photo_calendar.calendar;

import com.planner.photo_calendar.calendar.dto.IntegratedCalendarResponse;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.completionhistory.CompletionHistoryRepository;
import com.planner.photo_calendar.record.DailyRecordRepository;
import lombok.RequiredArgsConstructor;
import com.planner.photo_calendar.auth.CurrentOwner;
import com.planner.photo_calendar.common.time.ApplicationTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {
    private final CurrentOwner currentOwner;
    private final ApplicationTime applicationTime;
    private final CategoryRepository categoryRepository;

    private final DailyRecordRepository dailyRecordRepository;

    private final CompletionHistoryRepository completionHistoryRepository;

    @Transactional(readOnly = true)
    public List<IntegratedCalendarResponse> getIntegratedCalender(
            int year,
            int month
    ) {
        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<IntegratedCalendarResponse> response = new ArrayList<>();

        LocalDate today = applicationTime.today();
        long activeCount = categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(currentOwner.id()).size();
        while (!startDate.isAfter(endDate)){
            LocalDateTime startOfDay = ApplicationTime.startOfDayUtc(startDate);
            LocalDateTime nextDay = ApplicationTime.startOfDayUtc(startDate.plusDays(1));

            long totalCount = startDate.isBefore(today)
                    ? categoryRepository.countActiveCategoriesByOwnerAt(currentOwner.id(), startOfDay, nextDay)
                    : activeCount;

            long currentRecordCount = dailyRecordRepository.countByRecordDateAndCategoryOwnerId(startDate, currentOwner.id());

            long historyCount = startDate.isBefore(today)
                    ? completionHistoryRepository.countById_RecordDateAndCategoryOwnerId(startDate, currentOwner.id())
                    : 0;

            long completedCount = currentRecordCount + historyCount;

            response.add(
                    new IntegratedCalendarResponse(
                            startDate,
                            Math.toIntExact(completedCount),
                            Math.toIntExact(totalCount)
                    )
            );

            startDate = startDate.plusDays(1);
        }
        return response;
    }
}
