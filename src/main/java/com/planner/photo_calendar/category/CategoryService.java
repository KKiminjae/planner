package com.planner.photo_calendar.category;

import com.planner.photo_calendar.category.dto.AnnualRecordStatus;
import com.planner.photo_calendar.category.dto.request.CategoryCreateRequest;
import com.planner.photo_calendar.category.dto.request.CategoryReorderRequest;
import com.planner.photo_calendar.category.dto.response.AnnualRecordResponse;
import com.planner.photo_calendar.category.dto.response.CategoryResponse;
import com.planner.photo_calendar.category.dto.response.DailyCategoryResponse;
import com.planner.photo_calendar.category.dto.request.CategoryUpdateRequest;
import com.planner.photo_calendar.completionhistory.CompletionHistory;
import com.planner.photo_calendar.completionhistory.CompletionHistoryId;
import com.planner.photo_calendar.completionhistory.CompletionHistoryRepository;
import com.planner.photo_calendar.record.DailyRecord;
import com.planner.photo_calendar.record.DailyRecordRepository;
import lombok.RequiredArgsConstructor;
import com.planner.photo_calendar.auth.CurrentOwner;
import com.planner.photo_calendar.common.time.ApplicationTime;
import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final ApplicationTime applicationTime;
    private final CurrentOwner currentOwner;
    private final com.planner.photo_calendar.record.DailyRecordService dailyRecordService;
    private final CategoryRepository categoryRepository;

    private final DailyRecordRepository recordRepository;

    private final CompletionHistoryRepository completionHistoryRepository;

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request){
        int displayOrder = categoryRepository.findMaxDisplayOrderByOwnerId(currentOwner.id()) + 1;

        Category category = new Category(currentOwner.id(), request.name(),
                request.color(),
                displayOrder,
                request.isPrivate());

        Category savedCategory = categoryRepository.save(category);

        return CategoryResponse.from(savedCategory);
    }

    @Transactional (readOnly = true)
    public List<CategoryResponse> getCategories() {

        return categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(currentOwner.id())
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyCategoryResponse> getDailyCategories(LocalDate date) {
        if (!date.isBefore(applicationTime.today())) {
            return categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(currentOwner.id())
                    .stream().map(DailyCategoryResponse::from).toList();
        }
        return categoryRepository.findActiveCategoriesByOwnerAt(currentOwner.id(),
                        ApplicationTime.startOfDayUtc(date), ApplicationTime.startOfDayUtc(date.plusDays(1)))
                .stream().map(DailyCategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request){
        Category category = categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(id, currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "존재하지 않는 카테고리 입니다."));
        category.update(request.name(), request.color(), request.isPrivate());

        return CategoryResponse.from(category);
    }

    @Transactional
    public void reorder(CategoryReorderRequest request){
        List<Category> categories = categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(currentOwner.id());

        if(categories.size() != request.categoryIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY_ORDER, "모든 카테고리의 순서를 전달해야 합니다.");
        }

        if(request.categoryIds().stream().distinct().count() != request.categoryIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY_ORDER, "모든 카테고리의 순서를 중복 없이 전달해야 합니다.");
        }

        Map<Long, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(
                        Category::getId,
                        category -> category
                ));

        for (int i = 0; i < request.categoryIds().size(); i++) {

            Long categoryId = request.categoryIds().get(i);

            Category category = categoryMap.get(categoryId);

            if(category == null) {
                throw new BusinessException(ErrorCode.INVALID_CATEGORY_ORDER);
            }

            category.changeDisplayOrder(i+1);
        }
    }

    @Transactional
    public void delete(Long categoryId){
        Category category = categoryRepository.findActiveOwnedByIdForUpdate(categoryId, currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "존재하지 않는 카테고리입니다."));

        List<DailyRecord> records = recordRepository.findAllByCategoryId(categoryId);

        List<CompletionHistory> histories = records.stream()
                .map(record -> {
                    CompletionHistoryId historyId = new CompletionHistoryId(
                            categoryId, record.getRecordDate()
                    );
                    return new CompletionHistory(category, historyId);
                })
                .toList();

        completionHistoryRepository.saveAll(histories);

        for (DailyRecord record : records) {
            dailyRecordService.delete(record.getId());
        }

        category.delete();
    }

    @Transactional(readOnly = true)
    public List<AnnualRecordResponse> getAnnualRecords(
            Long categoryId,
            int year
    ) {
        Category category = categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "존재하지 않는 카테고리입니다."));

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        List<DailyRecord> records = recordRepository.findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                categoryId,
                startDate,
                endDate
        );

        Set<LocalDate> recordedDates = records.stream()
                .map(DailyRecord::getRecordDate)
                .collect(Collectors.toSet());

        LocalDate createDate = ApplicationTime.calendarDate(category.getCreatedAt());
        LocalDate today = applicationTime.today();

        ArrayList<AnnualRecordResponse> response = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);

            for (int day = 1; day <= 31; day++) {

                if(!yearMonth.isValidDay(day)) {
                    response.add(
                            new AnnualRecordResponse(
                                    month,
                                    day,
                                    AnnualRecordStatus.NO_DATE
                            )
                    );
                    continue;
                }
                LocalDate date = yearMonth.atDay(day);

                AnnualRecordStatus status;

                if(date.isBefore(createDate)) {
                    status = AnnualRecordStatus.NO_DATE;
                } else if(date.isAfter(today)){
                    status = AnnualRecordStatus.FUTURE;
                } else if(recordedDates.contains(date)){
                    status = AnnualRecordStatus.RECORDED;
                } else {
                    status = AnnualRecordStatus.MISSED;
                }

                response.add(
                        new AnnualRecordResponse(
                                month,
                                day,
                                status
                        )
                );
            }
        }
        return response;
    }
}
