package com.besenior.harucoding.generation;

import com.besenior.harucoding.generation.seed.SeedSet;
import com.besenior.harucoding.generation.seed.SeedSetRepository;
import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 검색: 요청 조건과 유사한 '시드 세트' top-k를 찾아 few-shot 예시 텍스트로 만든다.
 * 풀이 정보(Answer/Explanation)는 제외하고 구조(개념+문제 골격)만 노출해 베끼기를 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedRetriever {

    private final SeedSetRepository seedSetRepository;
    private final ProblemEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Value("${seed.retrieve.topk:3}")
    private int topK;

    /** 요청 조건으로 유사 시드 세트를 검색해 few-shot 예시 블록 텍스트 반환. */
    public String buildFewShotExamples(String category, String subcategory, int difficulty, String language) {
        String queryVec;
        try {
            queryVec = embeddingService.embed(buildQueryText(category, subcategory));
        } catch (Exception e) {
            log.warn("쿼리 임베딩 실패, 예시 없이 진행: {}", e.getMessage());
            return noExamples();
        }

        List<SeedSet> sets = seedSetRepository.findNearest(category, subcategory, language, difficulty, queryVec, topK);
        if (sets.isEmpty()) return noExamples();

        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (SeedSet s : sets) {
            sb.append("### Example Set ").append(i++).append("\n");
            sb.append(formatSet(s)).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String noExamples() {
        return "(시드 DB에서 검색된 유사 세트가 없습니다. 일반 지침에 따라 생성하세요.)";
    }

    private String buildQueryText(String category, String subcategory) {
        return (subcategory != null ? subcategory : category) + "\n" + category;
    }

    /** 시드 세트 1개를 '개념 + 문제 골격(정답 제외)' compact JSON으로 포맷. */
    private String formatSet(SeedSet s) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("Concept Explanation", s.getConceptExplanation());

        List<Map<String, Object>> compact = new ArrayList<>();
        if (s.getProblems() != null) {
            for (Map<String, Object> p : s.getProblems()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("Type", p.get("Type"));
                c.put("Title", p.get("Title"));
                c.put("Description", p.get("Description"));
                c.put("Constraints", p.getOrDefault("Constraints", List.of()));
                c.put("Signature", p.get("Signature"));
                c.put("IO Example", p.get("IO Example"));
                c.put("Code Skeleton", p.get("Code Skeleton"));
                compact.add(c);
            }
        }
        block.put("Problems", compact);

        try {
            return "```json\n"
                    + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(block)
                    + "\n```";
        } catch (Exception e) {
            return "(예시 포맷 실패)";
        }
    }
}
