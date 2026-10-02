package com.planner.photo_calendar.calendar;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class CalendarTimeIntegrationTest extends MySqlIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired CalendarService calendar;

    @Test
    void UTC상_같은날_생성된_카테고리를_한국시간_월경계로_나눠_집계한다() {
        Category beforeMidnight = new Category("자정 전", "#000000", 1, false);
        Category afterMidnight = new Category("자정 후", "#000000", 2, false);
        ReflectionTestUtils.setField(beforeMidnight, "createdAt", LocalDateTime.of(2026, 9, 30, 14, 59));
        ReflectionTestUtils.setField(afterMidnight, "createdAt", LocalDateTime.of(2026, 9, 30, 15, 15));
        for (Category category : new Category[]{beforeMidnight, afterMidnight}) {
            ReflectionTestUtils.setField(category, "deletedAt", LocalDateTime.of(2026, 10, 1, 14, 59));
            categories.saveAndFlush(category);
        }
        assertThat(calendar.getIntegratedCalender(2026, 9)).filteredOn(day -> day.date().equals(LocalDate.of(2026, 9, 30)))
                .singleElement().satisfies(day -> assertThat(day.totalCount()).isEqualTo(1));
        assertThat(calendar.getIntegratedCalender(2026, 10)).filteredOn(day -> day.date().equals(LocalDate.of(2026, 10, 1)))
                .singleElement().satisfies(day -> assertThat(day.totalCount()).isEqualTo(2));
        assertThat(calendar.getIntegratedCalender(2026, 10)).filteredOn(day -> day.date().equals(LocalDate.of(2026, 10, 2)))
                .singleElement().satisfies(day -> assertThat(day.totalCount()).isZero());
    }
}
