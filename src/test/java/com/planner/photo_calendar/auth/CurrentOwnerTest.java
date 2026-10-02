package com.planner.photo_calendar.auth;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CurrentOwnerTest {
    private final CurrentOwner currentOwner = new CurrentOwner();
    @AfterEach void 인증_정리() { SecurityContextHolder.clearContext(); }

    @Test
    void 미인증과_잘못된_주체는_기본_소유자로_처리하지_않는다() {
        assertDenied();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null, List.of()));
        assertDenied();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new OwnerPrincipal(1L, "owner", ""), null));
        assertDenied();
    }

    @Test
    void 인증된_주체의_소유자_ID를_사용한다() {
        OwnerPrincipal principal = new OwnerPrincipal(2L, "other", "");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertThat(currentOwner.id()).isEqualTo(2L);
    }

    private void assertDenied() {
        assertThatThrownBy(currentOwner::id).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
    }
}
