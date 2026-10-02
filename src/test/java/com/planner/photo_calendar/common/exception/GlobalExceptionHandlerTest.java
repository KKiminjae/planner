package com.planner.photo_calendar.common.exception;

import com.planner.photo_calendar.category.CategoryService;
import com.planner.photo_calendar.record.DailyRecordController;
import com.planner.photo_calendar.record.DailyRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.SQLException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private DailyRecordService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(DailyRecordService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DailyRecordController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingRecordReturns404() throws Exception {
        when(service.getRecord(1L)).thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        mvc.perform(get("/api/records/1")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    void duplicateReturns409() throws Exception {
        when(service.create(any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_DAILY_RECORD));
        mvc.perform(post("/api/records").contentType("application/json").content(validRequest()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DAILY_RECORD"));
    }

    @Test
    void invalidFieldsReturn400WithoutCallingService() throws Exception {
        mvc.perform(post("/api/records").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void malformedRequestAndMissingParameterReturn400() throws Exception {
        mvc.perform(post("/api/records").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/records")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/records").param("date", "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedFailureHidesInternalMessage() throws Exception {
        when(service.getRecord(1L)).thenThrow(new IllegalStateException("secret database detail"));
        mvc.perform(get("/api/records/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @Test
    void onlyKnownUniqueConstraintReturns409() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), "records.uq_records_category_date")));
        mvc.perform(post("/api/records").contentType("application/json").content(validRequest()))
                .andExpect(status().isConflict());
        doThrow(new DataIntegrityViolationException("other constraint")).when(service).create(any());
        mvc.perform(post("/api/records").contentType("application/json").content(validRequest()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void unsupportedMethodKeeps405() throws Exception {
        mvc.perform(put("/api/records/1")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidMonthAndYearReturn400() throws Exception {
        CategoryService categoryService = mock(CategoryService.class);
        MockMvc categoryMvc = MockMvcBuilders.standaloneSetup(
                new com.planner.photo_calendar.category.CategoryController(categoryService, service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        categoryMvc.perform(get("/api/categories/1/records").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        categoryMvc.perform(get("/api/categories/1/annual").param("year", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(categoryService, service);
    }

    @Test
    void businessDateAndOrderFailuresReturn400() throws Exception {
        when(service.create(any())).thenThrow(new BusinessException(ErrorCode.FUTURE_RECORD_NOT_ALLOWED));
        mvc.perform(post("/api/records").contentType("application/json").content(validRequest()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FUTURE_RECORD_NOT_ALLOWED"));
        doThrow(new BusinessException(ErrorCode.RECORD_BEFORE_CATEGORY_CREATION)).when(service).create(any());
        mvc.perform(post("/api/records").contentType("application/json").content(validRequest()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RECORD_BEFORE_CATEGORY_CREATION"));
        CategoryService categoryService = mock(CategoryService.class);
        doThrow(new BusinessException(ErrorCode.INVALID_CATEGORY_ORDER)).when(categoryService).reorder(any());
        MockMvc categoryMvc = MockMvcBuilders.standaloneSetup(
                new com.planner.photo_calendar.category.CategoryController(categoryService, service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        categoryMvc.perform(patch("/api/categories/reorder").contentType("application/json")
                .content("{\"categoryIds\":[1,1]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CATEGORY_ORDER"));
    }

    private String validRequest() {
        return "{\"categoryId\":1,\"recordDate\":\"2026-09-15\",\"recordTime\":\"09:00:00\",\"memo\":\"기록\"}";
    }
}
