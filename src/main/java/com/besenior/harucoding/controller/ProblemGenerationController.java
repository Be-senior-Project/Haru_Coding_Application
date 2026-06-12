package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.GenerateProblemRequest;
import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.generation.GenerationPipeline;
import com.besenior.harucoding.generation.ProblemSetSessionService;
import com.besenior.harucoding.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 문제 생성/세트 시작 엔드포인트.
 * - /start-set : (유저용) 안 푼 DB 문제 서빙, 없으면 즉석 생성
 * - /generate  : (관리/내부) 파라미터로 직접 생성
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemGenerationController {

    private final GenerationPipeline pipeline;
    private final ProblemSetSessionService sessionService;
    private final JwtProvider jwtProvider;

    private static final List<String> ALL_TYPES =
            List.of("Implementation", "Debugging", "Fill-in-the-blank");

    /** 세트 시작: 추천(안 푼 문제) 우선, 없으면 생성. 로그인 필요. */
    @PostMapping("/start-set")
    public ResponseEntity<List<ProblemResponse>> startSet(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "3") int count) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(sessionService.startSet(userId, count));
    }

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
