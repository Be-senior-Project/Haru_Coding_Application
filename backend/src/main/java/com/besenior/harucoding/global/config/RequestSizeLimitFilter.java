package com.besenior.harucoding.global.config;

import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 요청 본문 크기 제한(256KB).
 * JSON 요청에는 서블릿 컨테이너의 기본 크기 제한이 없어, 매우 큰 요청 하나로 서버 메모리를 크게 쓸 수 있다.
 * Content-Length가 한도를 넘으면 바로 413으로 거절하고, 길이를 밝히지 않은 요청(chunked)은
 * 읽은 양이 한도를 넘는 순간 읽기를 실패시킨다(이 경우 요청 형식 오류 400으로 응답된다).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.fail(ErrorCode.REQUEST_TOO_LARGE.getMessage())));
            return;
        }
        filterChain.doFilter(new LimitedBodyRequest(request, MAX_BODY_BYTES), response);
    }

    /** 본문을 limit 바이트까지만 읽을 수 있게 감싼 요청. */
    static class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream limited;

        LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limited == null) {
                limited = new LimitedInputStream(super.getInputStream(), limit);
            }
            return limited;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    static class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream in;
        private final long limit;
        private long count;

        LimitedInputStream(ServletInputStream in, long limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = in.read(buf, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) throws IOException {
            count += n;
            if (count > limit) {
                throw new IOException("요청 본문이 너무 큽니다 (" + limit + "바이트 초과)");
            }
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
