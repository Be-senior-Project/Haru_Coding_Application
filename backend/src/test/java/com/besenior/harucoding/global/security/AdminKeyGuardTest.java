package com.besenior.harucoding.global.security;

import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 관리용 API(비용 발생)를 관리자 키가 있을 때만 허용하는지 검증한다. */
class AdminKeyGuardTest {

    private void assertDenied(AdminKeyGuard guard, String providedKey) {
        assertThatThrownBy(() -> guard.check(providedKey))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
    }

    @Test
    void 관리자_키가_설정되지_않은_서버는_항상_거부한다() {
        AdminKeyGuard guard = new AdminKeyGuard("");

        assertDenied(guard, "");
        assertDenied(guard, "anything");
    }

    @Test
    void 헤더가_없으면_거부한다() {
        assertDenied(new AdminKeyGuard("admin-secret"), null);
    }

    @Test
    void 키가_다르면_거부한다() {
        assertDenied(new AdminKeyGuard("admin-secret"), "admin-secreT");
    }

    @Test
    void 키가_같으면_통과한다() {
        assertThatCode(() -> new AdminKeyGuard("admin-secret").check("admin-secret"))
                .doesNotThrowAnyException();
    }
}
