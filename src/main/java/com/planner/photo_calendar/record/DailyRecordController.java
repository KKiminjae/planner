package com.planner.photo_calendar.record;

import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;
import com.planner.photo_calendar.record.dto.request.DailyRecordUpdateRequest;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.record.dto.response.MonthlyRecordResponse;
import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class DailyRecordController {

    private final DailyRecordService dailyRecordService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyRecordResponse create(
            @Valid @RequestBody DailyRecordCreateRequest request
            ) {
        return dailyRecordService.create(request);
    }

    @GetMapping("/{id}")
    public DailyRecordResponse getRecord(
            @PathVariable Long id
    ) {
        return dailyRecordService.getRecord(id);
    }

    @GetMapping
    public List<DailyRecordResponse> getDailyRecords(
            @RequestParam LocalDate date
            ) {
        return dailyRecordService.getDailyRecords(date);
    }

    @PatchMapping("/{id}")
    public DailyRecordResponse update(
            @PathVariable Long id,
            @Valid @RequestBody DailyRecordUpdateRequest request
            ) {
        return dailyRecordService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id
    ) {
        dailyRecordService.delete(id);
    }
}
