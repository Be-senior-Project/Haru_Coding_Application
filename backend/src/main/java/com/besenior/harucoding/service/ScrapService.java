package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.ScrapResponse;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.ProblemScrap;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.ProblemScrapRepository;
import com.besenior.harucoding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 문제 스크랩(북마크) 등록·해제·조회. */
@Service
@RequiredArgsConstructor
public class ScrapService {

    private final ProblemScrapRepository problemScrapRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ScrapResponse> getScraps(Long userId) {
        return problemScrapRepository.findAllByUserId(userId).stream()
                .map(ScrapResponse::from)
                .toList();
    }

    /** 문제 상세 화면의 북마크 아이콘 상태 표시용. */
    @Transactional(readOnly = true)
    public boolean isScrapped(Long userId, Long problemId) {
        return problemScrapRepository.existsByUserIdAndProblemId(userId, problemId);
    }

    /**
     * 스크랩 토글. 이미 있으면 해제, 없으면 등록한다.
     * 등록/해제를 따로 두면 클라이언트가 현재 상태를 먼저 알아야 해서 왕복이 늘고,
     * 연타 시 상태가 어긋나기 쉬워 토글 하나로 둔다.
     *
     * @return 토글 후 스크랩 상태 (true=스크랩됨)
     */
    @Transactional
    public boolean toggleScrap(Long userId, Long problemId) {
        return problemScrapRepository.findByUserIdAndProblemId(userId, problemId)
                .map(scrap -> {
                    problemScrapRepository.delete(scrap);
                    return false;
                })
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    Problem problem = problemRepository.findById(problemId)
                            .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_NOT_FOUND));

                    problemScrapRepository.save(
                            ProblemScrap.builder()
                                    .user(user)
                                    .problem(problem)
                                    .build()
                    );
                    return true;
                });
    }
}
