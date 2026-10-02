package com.planner.photo_calendar.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@Transactional
public abstract class MySqlIntegrationTest {
    @org.junit.jupiter.api.BeforeEach
    void 기본_소유자로_인증한다() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.planner.photo_calendar.auth.OwnerPrincipal(1L, "owner", ""), null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_OWNER")));
    }

    @org.junit.jupiter.api.AfterEach
    void 인증을_정리한다() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
