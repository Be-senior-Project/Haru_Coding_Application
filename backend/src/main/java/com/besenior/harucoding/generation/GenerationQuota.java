package com.besenior.harucoding.generation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 하루 AI 문제 생성 한도.
 * 문제를 생성할 때마다 OpenAI 비용이 들어서, 계정 하나로 무제한 생성하는 것을 막는다.
 * 서버 메모리에 저장하므로 서버를 재시작하면 초기화되고, 서버가 여러 대면 대수만큼 한도가 늘어난다.
 */
@Component
public class GenerationQuota {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final int dailyLimit;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Usage> usage = new ConcurrentHashMap<>();

    private record Usage(LocalDate date, int count) {}

    @Autowired
    public GenerationQuota(@Value("${generation.daily-limit-per-user:30}") int dailyLimit) {
        this(dailyLimit, Clock.system(KST));
    }

    GenerationQuota(int dailyLimit, Clock clock) {
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    /**
     * 오늘 남은 한도 안에서 amount만큼 쓸 수 있으면 차감하고 true.
     * 넘으면 차감하지 않고 false. 날짜는 한국 시간 기준으로 자정에 초기화된다.
     */
    public boolean tryConsume(Long userId, int amount) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        boolean[] allowed = {false};
        usage.compute(userId, (id, current) -> {
            int used = (current == null || !current.date().equals(today)) ? 0 : current.count();
            if (used + amount > dailyLimit) {
                return new Usage(today, used);
            }
            allowed[0] = true;
            return new Usage(today, used + amount);
        });
        return allowed[0];
    }
}
