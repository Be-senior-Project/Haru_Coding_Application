package com.besenior.harucoding.service;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.ProblemEmbedding;
import com.besenior.harucoding.repository.ProblemEmbeddingRepository;
import com.besenior.harucoding.repository.ProblemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 문제 임베딩 생성/저장. (problems → problem_embeddings)
 * OpenAI text-embedding-3-small(1536차원)을 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemEmbeddingService {

    private final ProblemRepository problemRepository;
    private final ProblemEmbeddingRepository embeddingRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String URL = "https://api.openai.com/v1/embeddings";
    private static final String MODEL = "text-embedding-3-small";

    /** 임베딩이 없는 문제만 골라 생성·저장. 적재한 개수를 반환한다. */
    @Transactional
    public int backfillMissing() {
        Set<Long> have = embeddingRepository.findAll().stream()
                .map(ProblemEmbedding::getProblemId)
                .collect(Collectors.toSet());

        List<Problem> missing = problemRepository.findAll().stream()
                .filter(p -> !have.contains(p.getId()))
                .toList();

        int count = 0;
        for (Problem p : missing) {
            embeddingRepository.upsertEmbedding(p.getId(), embed(buildText(p)));
            count++;
        }
        log.info("임베딩 생성 완료: {}건 (전체 {} / 기존 {})", count,
                missing.size() + have.size(), have.size());
        return count;
    }

    /** 텍스트 한 건을 임베딩해 pgvector 리터럴("[v0,v1,...]") 문자열로 반환. */
    public String embed(String text) {
        Map<String, Object> body = Map.of("model", MODEL, "input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        try {
            JsonNode arr = objectMapper.readTree(response.getBody())
                    .path("data").get(0).path("embedding");
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(arr.get(i).asText());
            }
            return sb.append("]").toString();
        } catch (Exception e) {
            throw new RuntimeException("임베딩 응답 파싱 실패", e);
        }
    }

    /** 문제 1건의 임베딩을 생성해 바로 저장(벡터는 명시적 CAST로 upsert).
     *  buildText를 재사용하므로 backfill과 동일한 임베딩 축을 보장한다. */
    public void saveEmbedding(Problem p) {
        embeddingRepository.upsertEmbedding(p.getId(), embed(buildText(p)));
    }

    /** 임베딩 입력 텍스트: 개념/제목/설명/스켈레톤을 합쳐 의미를 풍부하게. */
    private String buildText(Problem p) {
        return String.join("\n",
                safe(p.getCategory()),
                safe(p.getSubcategory()),
                safe(p.getTitle()),
                safe(p.getDescription()),
                safe(p.getConceptExplanation()),
                safe(p.getCodeSkeleton()));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
