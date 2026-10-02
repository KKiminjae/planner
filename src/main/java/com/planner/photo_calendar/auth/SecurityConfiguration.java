package com.planner.photo_calendar.auth;

import com.planner.photo_calendar.common.exception.ErrorCode;
import com.planner.photo_calendar.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService ownerUserDetails(@Value("${auth.owner.username:owner}") String username,
                                       @Value("${auth.owner.password-hash:}") String passwordHash) {
        if (!username.matches("[A-Za-z0-9_.-]{1,50}")) {
            throw new IllegalStateException("APP_LOGIN_USERNAME은 1~50자의 영문·숫자·밑줄·점·하이픈으로 설정해 주세요.");
        }
        if (!passwordHash.isBlank() && !passwordHash.matches("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalStateException("APP_LOGIN_PASSWORD_HASH에 올바른 BCrypt 해시를 설정해 주세요.");
        }
        return requested -> {
            // 설정이 없을 때 기본 비밀번호나 자동 생성 계정을 제공하지 않습니다.
            if (passwordHash.isBlank() || !username.equals(requested)) {
                throw new UsernameNotFoundException("로그인 정보를 확인해 주세요.");
            }
            return new OwnerPrincipal(1L, username, passwordHash);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/auth/csrf", "/error").permitAll()
                    .anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(login -> login.loginPage("/api/auth/login").loginProcessingUrl("/api/auth/login")
                    .successHandler((request, response, authentication) -> {
                        response.setStatus(200);
                        response.setContentType("application/json");
                        response.setHeader("Cache-Control", "no-store");
                        mapper.writeValue(response.getOutputStream(), AuthResponse.from((OwnerPrincipal) authentication.getPrincipal()));
                    })
                    .failureHandler((request, response, exception) -> error(response, mapper, ErrorCode.INVALID_CREDENTIALS))
                    .permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, exception) -> error(response, mapper, ErrorCode.AUTHENTICATION_REQUIRED))
                    .accessDeniedHandler((request, response, exception) -> error(response, mapper, ErrorCode.REQUEST_FORBIDDEN)));
        return http.build();
    }

    private static void error(HttpServletResponse response, ObjectMapper mapper, ErrorCode code) throws IOException {
        response.setStatus(code.status());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), ErrorResponse.from(code));
    }
}
