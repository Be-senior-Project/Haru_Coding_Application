package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.WrongNoteResponse;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 오답노트 조회. 별도 테이블 없이 user_problem_records의 오답 기록을 그대로 활용한다. */
@Service
@RequiredArgsConstructor
public class WrongNoteService {

    private final UserProblemRecordRepository userProblemRecordRepository;

    /**
     * 사용자가 틀린 문제 목록(최신순).
     * 같은 문제를 여러 번 틀렸어도 가장 최근 오답 1건만 남긴다 —
     * 목록에 같은 문제가 여러 줄 뜨면 복습 목록으로 쓰기 어렵기 때문.
     * 나중에 정답 처리된 문제도 그대로 남는다(복습용).
     */
    @Transactional(readOnly = true)
    public List<WrongNoteResponse> getWrongNotes(Long userId) {
        List<UserProblemRecord> records = userProblemRecordRepository.findWrongRecordsByUserId(userId);

        // 쿼리가 최신순이므로 문제별 첫 번째 항목이 가장 최근 오답이다.
        Map<Long, UserProblemRecord> latestByProblem = new LinkedHashMap<>();
        for (UserProblemRecord record : records) {
            latestByProblem.putIfAbsent(record.getProblem().getId(), record);
        }

        List<WrongNoteResponse> result = new ArrayList<>(latestByProblem.size());
        latestByProblem.values().forEach(record -> result.add(WrongNoteResponse.from(record)));
        return result;
    }
}
