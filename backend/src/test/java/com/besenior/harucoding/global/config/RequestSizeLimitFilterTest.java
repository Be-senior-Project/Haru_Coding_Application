package com.besenior.harucoding.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 요청 본문 크기 제한(256KB). */
class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new ObjectMapper());

    private MockHttpServletRequest post(int bodyBytes) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/problems/1/attempt");
        req.setContent(new byte[bodyBytes]);
        return req;
    }

    @Test
    void 본문이_한도_이내면_그대로_통과시킨다() throws Exception {
        AtomicBoolean passed = new AtomicBoolean(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(post(1024), res, (rq, rs) -> {
            rq.getInputStream().readAllBytes();
            passed.set(true);
        });

        assertThat(passed).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void Content_Length가_한도를_넘으면_413으로_거절하고_다음으로_넘기지_않는다() throws Exception {
        AtomicBoolean passed = new AtomicBoolean(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(post((int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1), res, (rq, rs) -> passed.set(true));

        assertThat(passed).isFalse();
        assertThat(res.getStatus()).isEqualTo(413);
        assertThat(res.getContentAsString()).contains("요청 크기가 너무 큽니다");
    }

    @Test
    void 길이를_밝히지_않은_요청은_한도를_넘게_읽는_순간_실패한다() throws Exception {
        MockHttpServletRequest big = post((int) RequestSizeLimitFilter.MAX_BODY_BYTES + 10);
        // chunked 전송처럼 Content-Length를 모르는 요청
        HttpServletRequestWrapper unknownLength = new HttpServletRequestWrapper(big) {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        AtomicBoolean failed = new AtomicBoolean(false);
        FilterChain chain = (rq, rs) -> {
            try {
                rq.getInputStream().readAllBytes();
            } catch (IOException e) {
                failed.set(true);
            }
        };

        filter.doFilter(unknownLength, new MockHttpServletResponse(), chain);

        assertThat(failed).isTrue();
    }

    @Test
    void 한도를_넘어_읽으면_IOException이_난다() {
        MockHttpServletRequest big = post(100);
        RequestSizeLimitFilter.LimitedBodyRequest limited = new RequestSizeLimitFilter.LimitedBodyRequest(big, 10);

        assertThatThrownBy(() -> limited.getInputStream().readAllBytes()).isInstanceOf(IOException.class);
    }
}
