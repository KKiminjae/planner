package com.planner.photo_calendar.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class SecurityConfigurationTest {
    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void 비밀번호_설정이_없으면_기본_계정을_허용하지_않는다() {
        assertThatThrownBy(() -> configuration.ownerUserDetails("owner", "").loadUserByUsername("owner"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void 유효한_해시는_소유자_1의_계정으로만_등록한다() {
        String hash = new BCryptPasswordEncoder(4).encode("test-password");
        OwnerPrincipal principal = (OwnerPrincipal) configuration.ownerUserDetails("owner", hash).loadUserByUsername("owner");
        assertThat(principal.ownerId()).isEqualTo(1L);
        assertThat(configuration.passwordEncoder().matches("test-password", principal.getPassword())).isTrue();
        assertThatThrownBy(() -> configuration.ownerUserDetails("owner", hash).loadUserByUsername("other"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void 잘못된_해시와_계정_이름은_설정_오류로_거부하고_해시_내용을_노출하지_않는다() {
        assertThatThrownBy(() -> configuration.ownerUserDetails("owner", "plain-password"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("plain-password");
        assertThatThrownBy(() -> configuration.ownerUserDetails("", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
