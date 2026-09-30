package com.planner.photo_calendar.category;

import com.planner.photo_calendar.category.dto.request.CategoryCreateRequest;
import com.planner.photo_calendar.category.dto.request.CategoryReorderRequest;
import com.planner.photo_calendar.category.dto.response.AnnualRecordResponse;
import com.planner.photo_calendar.category.dto.response.CategoryResponse;
import com.planner.photo_calendar.category.dto.request.CategoryUpdateRequest;
import com.planner.photo_calendar.record.DailyRecordRepository;
import com.planner.photo_calendar.record.DailyRecordService;
import com.planner.photo_calendar.record.dto.response.MonthlyRecordResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    private final DailyRecordService dailyRecordService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(
            @Valid @RequestBody CategoryCreateRequest request
            ){
        return categoryService.create(request);
    }

    @GetMapping
    public List<CategoryResponse> getCategories(){
        return categoryService.getCategories();
    }

    @GetMapping("/{id}/records")
    public List<MonthlyRecordResponse> getMonthlyRecords(
            @PathVariable Long id,
            @RequestParam @Min(1) @Max(9999) int year,
            @RequestParam @Min(1) @Max(12) int month
    ) {
        return dailyRecordService.getMonthlyRecords(id, year, month);
    }

    @GetMapping("/{id}/annual")
    public List<AnnualRecordResponse> getAnnualRecords(
            @PathVariable Long id,
            @RequestParam @Min(1) @Max(9999) int year
    ) {
        return categoryService.getAnnualRecords(id, year);
    }

    @PatchMapping("/{id}")
    public CategoryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request
            ) {
        return categoryService.update(id, request);
    }

    @PatchMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(
            @Valid @RequestBody CategoryReorderRequest request
            ) {
        categoryService.reorder(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id
    ) {
        categoryService.delete(id);
    }

}
