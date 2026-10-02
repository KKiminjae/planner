package com.planner.photo_calendar.calendar;

import com.planner.photo_calendar.calendar.dto.IntegratedCalendarResponse;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.completionhistory.CompletionHistoryRepository;
import com.planner.photo_calendar.record.DailyRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.planner.photo_calendar.auth.CurrentOwner;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DailyRecordRepository dailyRecordRepository;

    @Mock
    private CompletionHistoryRepository completionHistoryRepository;

    @Mock
    private CurrentOwner currentOwner;

    @Mock
    private com.planner.photo_calendar.common.time.ApplicationTime applicationTime;

    @BeforeEach
    void 로그인_소유자를_설정한다() {
        org.mockito.Mockito.lenient().when(currentOwner.id()).thenReturn(1L);
        org.mockito.Mockito.lenient().when(applicationTime.today()).thenReturn(LocalDate.of(2027, 1, 1));
    }

    @InjectMocks
    private CalendarService calendarService;

    @Test
    void 오늘은_삭제된_카테고리와_완료이력을_제외한다() {
        when(applicationTime.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(java.util.Collections.nCopies(4, new com.planner.photo_calendar.category.Category(1L, "기록", "#F4BED2", 1, false)));
        when(dailyRecordRepository.countByRecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L))).thenReturn(2L);
        var result = calendarService.getIntegratedCalender(2026, 9);
        assertEquals(4, result.get(0).totalCount());
        assertEquals(2, result.get(0).completedCount());
        org.mockito.Mockito.verifyNoInteractions(completionHistoryRepository);
        verify(categoryRepository, org.mockito.Mockito.never()).countActiveCategoriesByOwnerAt(any(), any(), any());
    }

    @Test
    void 해당_월의_모든_날짜별_통합현황을_반환한다() {
        when(categoryRepository.countActiveCategoriesByOwnerAt(eq(1L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(3L);
        when(dailyRecordRepository.countByRecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L)))
                .thenReturn(2L);
        when(completionHistoryRepository.countById_RecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L)))
                .thenReturn(0L);

        List<IntegratedCalendarResponse> responses =
                calendarService.getIntegratedCalender(2026, 9);

        assertEquals(30, responses.size());
        assertEquals(LocalDate.of(2026, 9, 1), responses.get(0).date());
        assertEquals(2, responses.get(0).completedCount());
        assertEquals(3, responses.get(0).totalCount());
        assertEquals(LocalDate.of(2026, 9, 30), responses.get(29).date());

        verify(categoryRepository, times(30)).countActiveCategoriesByOwnerAt(eq(1L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        verify(dailyRecordRepository, times(30))
                .countByRecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L));
        verify(completionHistoryRepository, times(30))
                .countById_RecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L));
    }

    @Test
    void 현재기록과_삭제된_카테고리의_완료이력을_합산한다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 15);

        when(categoryRepository.countActiveCategoriesByOwnerAt(eq(1L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(4L);
        when(dailyRecordRepository.countByRecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L)))
                .thenAnswer(invocation ->
                        targetDate.equals(invocation.getArgument(0)) ? 2L : 0L
                );
        when(completionHistoryRepository.countById_RecordDateAndCategoryOwnerId(any(LocalDate.class), eq(1L)))
                .thenAnswer(invocation ->
                        targetDate.equals(invocation.getArgument(0)) ? 1L : 0L
                );

        List<IntegratedCalendarResponse> responses =
                calendarService.getIntegratedCalender(2026, 9);

        IntegratedCalendarResponse targetResponse = responses.stream()
                .filter(response -> response.date().equals(targetDate))
                .findFirst()
                .orElseThrow();

        assertEquals(3, targetResponse.completedCount());
        assertEquals(4, targetResponse.totalCount());
    }

    @Test
    void 평년과_윤년_2월의_날짜수를_정확히_반환한다() {
        List<IntegratedCalendarResponse> commonYearResponses =
                calendarService.getIntegratedCalender(2027, 2);
        List<IntegratedCalendarResponse> leapYearResponses =
                calendarService.getIntegratedCalender(2028, 2);

        assertEquals(28, commonYearResponses.size());
        assertEquals(LocalDate.of(2027, 2, 28), commonYearResponses.get(27).date());
        assertEquals(29, leapYearResponses.size());
        assertEquals(LocalDate.of(2028, 2, 29), leapYearResponses.get(28).date());
    }
}
