package com.planner.photo_calendar.calendar;

import com.planner.photo_calendar.calendar.dto.IntegratedCalendarResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CalendarController.class)
class CalendarControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CalendarService service;

    @Test
    void 통합_달력_조회는_연월을_전달하고_날짜별_완료수와_전체수를_반환한다() throws Exception {
        when(service.getIntegratedCalender(2026, 9)).thenReturn(List.of(
                new IntegratedCalendarResponse(LocalDate.of(2026, 9, 15), 2, 3)));
        mvc.perform(get("/api/calendar/integrated").param("year", "2026").param("month", "9"))
                .andExpect(status().isOk()).andExpect(content().json("""
                        [{"date":"2026-09-15","completedCount":2,"totalCount":3}]
                        """));
        verify(service).getIntegratedCalender(2026, 9);
    }

    @ParameterizedTest
    @CsvSource({",9", "2026,", "0,9", "10000,9", "2026,0", "2026,13"})
    void 통합_달력은_연월이_없거나_범위를_벗어나면_서비스_호출_없이_400을_반환한다(String year, String month) throws Exception {
        mvc.perform(get("/api/calendar/integrated").param("year", year).param("month", month))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
}
