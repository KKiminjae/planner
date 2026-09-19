package com.planner.photo_calendar.calendar;

import com.planner.photo_calendar.calendar.dto.IntegratedCalendarResponse;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.completionhistory.CompletionHistoryRepository;
import com.planner.photo_calendar.record.DailyRecordRepository;
import lombok.RequiredArgsConstructor;
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

        while (!startDate.isAfter(endDate)){
            LocalDateTime startOfDay = startDate.atStartOfDay();
            LocalDateTime nextDay = startDate.plusDays(1).atStartOfDay();

            long totalCount = categoryRepository.countActiveCategoriesAt(startOfDay, nextDay);

            long currentRecordCount = dailyRecordRepository.countByRecordDate(startDate);

            long historyCount = completionHistoryRepository.countById_RecordDate(startDate);

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
