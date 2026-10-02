package com.planner.photo_calendar.auth;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.record.DailyRecord;
import com.planner.photo_calendar.record.DailyRecordRepository;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@AutoConfigureMockMvc
class AuthIntegrationTest extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CategoryRepository categories;
    @Autowired DailyRecordRepository records;
    private static final String TEST_HASH = new BCryptPasswordEncoder(4).encode("test-password");

    @DynamicPropertySource
    static void loginProperties(DynamicPropertyRegistry registry) {
        registry.add("auth.owner.username", () -> "owner");
        registry.add("auth.owner.password-hash", () -> TEST_HASH);
    }

    @Test
    void 미인증_조회는_리다이렉트_없이_401_공통_오류를_반환한다() throws Exception {
        for (String path : new String[]{"/api/categories", "/api/records", "/api/calendar/integrated", "/api/records/1/photo-url", "/api/auth/me"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(jsonPath("$.fieldErrors").isEmpty());
        }
    }

    @Test
    void 로그인도_CSRF가_필요하고_미인증_변경요청은_보호한다() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "owner").param("password", "test-password"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REQUEST_FORBIDDEN"));
        mvc.perform(post("/api/categories").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 실제_세션_로그인_CSRF_갱신_변경요청_로그아웃을_검증한다() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String beforeId = session.getId();
        String beforeToken = mapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", beforeToken)
                        .param("username", "owner").param("password", "test-password"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(session.getId()).isNotEqualTo(beforeId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(1)).andExpect(jsonPath("$.password").doesNotExist());
        String body = """
                {"name":"인증된 카테고리","color":"#FF0000","isPrivate":false,"ownerId":2}
                """;
        mvc.perform(post("/api/categories").session(session).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").session(session).header("X-CSRF-TOKEN", beforeToken)
                        .contentType("application/json").content(body)).andExpect(status().isForbidden());
        String nextToken = csrfToken(session);
        MvcResult created = mvc.perform(post("/api/categories").session(session).header("X-CSRF-TOKEN", nextToken)
                        .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        Long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(categories.findById(id).orElseThrow().getOwnerId()).isEqualTo(1L);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", nextToken))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_이름과_비밀번호는_동일한_401을_반환한다() throws Exception {
        for (String[] credentials : new String[][]{{"owner", "wrong"}, {"missing", "test-password"}}) {
            mvc.perform(post("/api/auth/login").with(csrf()).param("username", credentials[0]).param("password", credentials[1]))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("로그인 정보를 확인해 주세요."));
        }
    }

    @Test
    void 실제_로그인_세션도_다른_소유자의_기록을_조회_수정_삭제할_수_없다() throws Exception {
        Category foreign = categories.saveAndFlush(new Category(2L, "다른 소유자", "#FF0000", 1, false));
        DailyRecord record = records.saveAndFlush(new DailyRecord(foreign, LocalDate.now(), LocalTime.NOON, "비공개 메모", null));
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf")).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String token = mapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .param("username", "owner").param("password", "test-password")).andExpect(status().isOk());
        token = csrfToken(session);
        mvc.perform(get("/api/records/" + record.getId()).session(session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        mvc.perform(patch("/api/records/" + record.getId()).session(session).header("X-CSRF-TOKEN", token)
                        .contentType("application/json").content("""
                        {"recordTime":"12:00:00","memo":"바꾸기"}
                        """))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        mvc.perform(delete("/api/records/" + record.getId()).session(session).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        assertThat(records.findById(record.getId()).orElseThrow().getMemo()).isEqualTo("비공개 메모");
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
