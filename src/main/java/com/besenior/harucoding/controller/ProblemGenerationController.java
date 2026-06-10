package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.GenerateProblemRequest;
import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.generation.GenerationPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 문제 생성 엔드포인트 (파이프라인 입구).
 * retrieve → generate → verify → embed → save 후 생성 문제(정답 숨김)를 반환.
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemGenerationController {

    private final GenerationPipeline pipeline;

    private static final List<String> ALL_TYPES =
            List.of("Implementation", "Debugging", "Fill-in-the-blank");

    @PostMapping("/generate")
    public ResponseEntity<List<ProblemResponse>> generate(@RequestBody GenerateProblemRequest req) {
        if (req.getCategory() == null || req.getDifficulty() == null || req.getLanguage() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<String> types = (req.getTypes() == null || req.getTypes().isEmpty())
                ? ALL_TYPES : req.getTypes();
        int problemsPerSet = req.getProblemsPerSet() == null ? 3 : req.getProblemsPerSet();

        List<Problem> problems = pipeline.generateAndSave(
                req.getCategory(),
                req.getSubcategory(),
                req.getDifficulty(),
                req.getLanguage(),
                types,
                problemsPerSet);

        List<ProblemResponse> body = problems.stream()
                .map(p -> ProblemResponse.from(p, false)) // 풀이 전: 정답 미포함
                .toList();
        return ResponseEntity.ok(body);
    }
}
