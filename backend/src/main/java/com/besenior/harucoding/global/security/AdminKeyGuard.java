package com.besenior.harucoding.global.security;

import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 관리용 API(문제 직접 생성, 임베딩 일괄 생성) 접근 확인.
 * 둘 다 호출할 때마다 OpenAI 비용이 들고, 가입에 이메일 인증이 없어 회원 로그인만으로는 열어둘 수 없다.
 * 사용 예: 요청 헤더 {@code X-Admin-Key: <ADMIN_API_KEY 값>}
 */
@Component
public class AdminKeyGuard {

    public static final String HEADER = "X-Admin-Key";

    private final byte[] adminKey;

    public AdminKeyGuard(@Value("${admin.api-key:}") String adminKey) {
        this.adminKey = adminKey == null ? new byte[0] : adminKey.strip().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 헤더 값이 설정된 관리자 키와 같지 않으면 403(ADMIN_ONLY).
     * 관리자 키가 설정되지 않은 서버에서는 관리용 API를 항상 거부한다.
     */
    public void check(String providedKey) {
        if (adminKey.length == 0 || providedKey == null
                // 비교 시간으로 키를 한 글자씩 추측하지 못하도록 길이와 무관하게 일정한 시간으로 비교
                || !MessageDigest.isEqual(adminKey, providedKey.strip().getBytes(StandardCharsets.UTF_8))) {
            throw new CustomException(ErrorCode.ADMIN_ONLY);
        }
    }
}
