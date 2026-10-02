package com.planner.photo_calendar.category;

import com.planner.photo_calendar.category.dto.request.*;
import com.planner.photo_calendar.category.dto.response.*;
import com.planner.photo_calendar.category.dto.AnnualRecordStatus;
import com.planner.photo_calendar.record.DailyRecordService;
import com.planner.photo_calendar.record.dto.response.MonthlyRecordResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CategoryService service;
    @MockitoBean DailyRecordService recordService;

    private final CategoryResponse category = new CategoryResponse(7L, "운동", "#123456", 2, true);
    private final String body = """
            {"name":"운동","color":"#123456","isPrivate":true}
            """;

    @Test
    void 카테고리_생성은_201과_카테고리_정보를_반환한다() throws Exception {
        CategoryCreateRequest request = new CategoryCreateRequest("운동", "#123456", true);
        when(service.create(request)).thenReturn(category);
        mvc.perform(post("/api/categories").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":7,"name":"운동","color":"#123456","displayOrder":2,"isPrivate":true}
                        """));
        verify(service).create(request);
    }

    @Test
    void 카테고리_목록을_JSON_배열로_반환한다() throws Exception {
        when(service.getCategories()).thenReturn(List.of(category));
        mvc.perform(get("/api/categories")).andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":7,"name":"운동","color":"#123456","displayOrder":2,"isPrivate":true}]
                        """));
        verify(service).getCategories();
    }

    @Test
    void 카테고리_수정은_ID와_요청값을_전달하고_결과를_반환한다() throws Exception {
        CategoryUpdateRequest request = new CategoryUpdateRequest("운동", "#123456", true);
        when(service.update(7L, request)).thenReturn(category);
        mvc.perform(patch("/api/categories/7").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"id":7,"name":"운동","color":"#123456","displayOrder":2,"isPrivate":true}
                        """));
        verify(service).update(7L, request);
    }

    @Test
    void 카테고리_순서_변경은_요청_순서를_전달하고_204와_빈_본문을_반환한다() throws Exception {
        mvc.perform(patch("/api/categories/reorder").contentType("application/json")
                        .content("""
                        {"categoryIds":[7,3]}
                        """))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).reorder(new CategoryReorderRequest(List.of(7L, 3L)));
    }

    @Test
    void 카테고리_삭제는_ID를_전달하고_204와_빈_본문을_반환한다() throws Exception {
        mvc.perform(delete("/api/categories/7"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(7L);
    }

    @Test
    void 월간_기록_조회는_카테고리_ID와_연월을_전달하고_기록을_반환한다() throws Exception {
        when(recordService.getMonthlyRecords(7L, 2026, 9)).thenReturn(List.of(
                new MonthlyRecordResponse(11L, LocalDate.of(2026, 9, 15), LocalTime.of(9, 30), null)));
        mvc.perform(get("/api/categories/7/records").param("year", "2026").param("month", "9"))
                .andExpect(status().isOk()).andExpect(content().json("""
                        [{"id":11,"recordDate":"2026-09-15","recordTime":"09:30:00","imageKey":null}]
                        """));
        verify(recordService).getMonthlyRecords(7L, 2026, 9);
    }

    @Test
    void 연간_기록_조회는_ID와_연도를_전달하고_기록_상태를_문자열로_반환한다() throws Exception {
        when(service.getAnnualRecords(7L, 2026)).thenReturn(List.of(
                new AnnualRecordResponse(9, 15, AnnualRecordStatus.RECORDED)));
        mvc.perform(get("/api/categories/7/annual").param("year", "2026"))
                .andExpect(status().isOk()).andExpect(content().json("""
                        [{"month":9,"day":15,"status":"RECORDED"}]
                        """));
        verify(service).getAnnualRecords(7L, 2026);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"isPrivate\":true}",
            "{\"name\":\" \",\"color\":\"#123456\",\"isPrivate\":true}",
            "{\"name\":\"운동\",\"color\":\" \",\"isPrivate\":true}",
            "{\"color\":\"#123456\",\"isPrivate\":true}",
            "{\"name\":\"운동\",\"isPrivate\":true}"
    })
    void 카테고리_생성과_수정은_이름이나_색상이_없거나_공백이면_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        for (String method : List.of("POST", "PATCH")) {
            String url = method.equals("POST") ? "/api/categories" : "/api/categories/7";
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), url)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
        verifyNoInteractions(service, recordService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"categoryIds\":[]}", "{\"categoryIds\":[7,null]}"})
    void 순서_변경은_목록이_없거나_비어있거나_null을_포함하면_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        mvc.perform(patch("/api/categories/reorder").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        verifyNoInteractions(service, recordService);
    }

    @ParameterizedTest
    @CsvSource({",9", "2026,", "0,9", "10000,9", "2026,0", "2026,13"})
    void 월간_조회는_연월이_없거나_범위를_벗어나면_서비스_호출_없이_400을_반환한다(String year, String month) throws Exception {
        mvc.perform(get("/api/categories/7/records").param("year", year).param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service, recordService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "10000"})
    void 연간_조회는_연도가_없거나_범위를_벗어나면_서비스_호출_없이_400을_반환한다(String year) throws Exception {
        mvc.perform(get("/api/categories/7/annual").param("year", year))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service, recordService);
    }

    @Test
    void 카테고리와_월간_기록이_없으면_빈_JSON_배열을_반환한다() throws Exception {
        when(service.getCategories()).thenReturn(List.of());
        when(recordService.getMonthlyRecords(7L, 2026, 9)).thenReturn(List.of());
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/categories/7/records").param("year", "2026").param("month", "9"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).getCategories();
        verify(recordService).getMonthlyRecords(7L, 2026, 9);
    }
}
