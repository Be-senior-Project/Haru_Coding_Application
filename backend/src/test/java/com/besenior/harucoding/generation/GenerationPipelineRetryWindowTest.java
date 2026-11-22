package com.besenior.harucoding.generation;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 문제 생성 재시도가 앱 응답 시간 제한을 넘기지 않도록 재시도 허용 시간 안에서만 일어나는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class GenerationPipelineRetryWindowTest {

    @Mock private SeedRetriever seedRetriever;
    @Mock private ProblemGenerationClient generationClient;
    @Mock private CodeVerifier codeVerifier;
    @Mock private ProblemEmbeddingService embeddingService;
    @Mock private ProblemRepository problemRepository;

    /** 생성 응답은 계속 실패(null)한다. 재시도 허용 시간만 바꿔 가며 시도 횟수를 본다. */
    private List<Problem> generateWith(Duration retryWindow) {
        GenerationPipeline pipeline = new GenerationPipeline(seedRetriever, generationClient, codeVerifier,
                embeddingService, problemRepository, new ObjectMapper());
        ReflectionTestUtils.setField(pipeline, "retryWindow", retryWindow);
        return pipeline.generateAndSave("Basic/Introductory", null, 0, "Python", List.of("Implementation"), 1);
    }

    @Test
    void 재시도_허용_시간이_지나면_다시_시도하지_않는다() {
        assertThat(generateWith(Duration.ZERO)).isEmpty();
        verify(generationClient, times(1)).generateSet(any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void 허용_시간_안이면_최대_3회까지_다시_시도한다() {
        assertThat(generateWith(Duration.ofMinutes(1))).isEmpty();
        verify(generationClient, times(3)).generateSet(any(), any(), any(), any(), any(), anyInt(), any());
    }
}
