package com.besenior.harucoding.generation.seed;

import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * resources/json/all_seeds.json → seed_sets 적재.
 * 각 세트의 개념을 임베딩(text-embedding-3-small)해 RAG few-shot 검색에 사용.
 * 멱등: seed_sets에 이미 데이터가 있으면 스킵.
 *
 * 주의: ddl-auto=create면 재시작마다 테이블이 비워져 매번 재적재(=재임베딩)된다.
 *       영속화하려면 ddl-auto를 update/validate로 바꾸거나 seed.autoload=false로 끈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedLoader implements ApplicationRunner {

    private final SeedSetRepository seedSetRepository;
    private final ProblemEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Value("${seed.autoload:true}")
    private boolean autoload;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoload) {
            log.info("seed.autoload=false → 시드 적재 스킵");
            return;
        }
        long existing = seedSetRepository.count();
        if (existing > 0) {
            log.info("시드 이미 적재됨: {}건 → 스킵", existing);
            return;
        }
        try {
            JsonNode root;
            ClassPathResource res = new ClassPathResource("json/all_seeds.json");
            try (InputStream is = res.getInputStream()) {
                root = objectMapper.readTree(is);
            }
            if (!root.isArray()) {
                log.warn("all_seeds.json 형식 오류(배열 아님), 적재 중단");
                return;
            }

            int loaded = 0;
            for (JsonNode entry : root) {
                String category = entry.path("category").asText();
                String subcategory = entry.hasNonNull("subcategory") ? entry.get("subcategory").asText() : null;
                int difficulty = parseDifficulty(entry.path("difficulty").asText("0"));
                String language = entry.path("language").asText();
                String concept = entry.path("Set").path("Concept Explanation").asText("");

                List<Map<String, Object>> problems = objectMapper.convertValue(
                        entry.path("Problems"), new TypeReference<List<Map<String, Object>>>() {});

                String embedding = embeddingService.embed(buildEmbedText(concept, category, subcategory));

                // jsonb 컬럼은 엔티티로 저장(벡터는 null로 두고), 벡터는 명시적 CAST로 갱신
                SeedSet seed = seedSetRepository.save(SeedSet.builder()
                        .category(category)
                        .subcategory(subcategory)
                        .difficulty(difficulty)
                        .language(language)
                        .conceptExplanation(concept)
                        .problems(problems)
                        .embedding(null)
                        .build());
                seedSetRepository.updateEmbedding(seed.getId(), embedding);
                loaded++;
                if (loaded % 50 == 0) log.info("시드 적재 진행: {}건...", loaded);
            }
            log.info("시드 적재 완료: {}건", loaded);
        } catch (Exception e) {
            log.error("시드 적재 실패: {}", e.getMessage(), e);
        }
    }

    private int parseDifficulty(String d) {
        try {
            return Integer.parseInt(d.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildEmbedText(String concept, String category, String subcategory) {
        StringBuilder sb = new StringBuilder();
        if (concept != null && !concept.isEmpty()) sb.append(concept).append("\n");
        sb.append(category);
        if (subcategory != null && !subcategory.isEmpty()) sb.append("\n").append(subcategory);
        return sb.toString();
    }
}
