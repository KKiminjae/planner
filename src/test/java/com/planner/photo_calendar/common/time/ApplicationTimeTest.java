package com.planner.photo_calendar.common.time;

import com.planner.photo_calendar.photo.Photo;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTimeTest {
    @Test
    void UTC가_전날이어도_한국시간의_오늘을_반환한다() {
        ApplicationTime time = new ApplicationTime(Clock.fixed(Instant.parse("2026-09-30T15:30:00Z"), ZoneOffset.UTC));
        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void 한국시간_자정의_전후를_서로_다른_날짜로_구분한다() {
        assertThat(ApplicationTime.calendarDate(LocalDateTime.of(2026, 9, 30, 14, 59, 59)))
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(ApplicationTime.calendarDate(LocalDateTime.of(2026, 9, 30, 15, 0)))
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ApplicationTime.startOfDayUtc(LocalDate.of(2026, 10, 1)))
                .isEqualTo(LocalDateTime.of(2026, 9, 30, 15, 0));
    }

    @Test
    void 호스트_시간대를_바꿔도_사진_유예기간의_저장시각은_UTC다() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            LocalDateTime before = LocalDateTime.now(Clock.systemUTC());
            Photo photo = new Photo("photos/test.png", "image/png", 69);
            photo.markUploaded();
            photo.attach(1L);
            photo.detach(1L);
            LocalDateTime after = LocalDateTime.now(Clock.systemUTC());
            assertThat(photo.getCreatedAt()).isBetween(before, after);
            assertThat(photo.getUploadedAt()).isBetween(before, after);
            assertThat(photo.getUnlinkedAt()).isBetween(before, after);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
