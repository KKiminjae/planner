package com.planner.photo_calendar.calendar;

import com.planner.photo_calendar.calendar.dto.IntegratedCalendarResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/integrated")
    public List<IntegratedCalendarResponse> getIntegratedCalendar(
            @RequestParam int year,
            @RequestParam int month
    ) {
        return calendarService.getIntegratedCalender(year, month);
    }
}
