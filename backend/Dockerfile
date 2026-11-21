# ── 빌드 스테이지 ──────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew clean bootJar -x test --no-daemon

# ── 런타임 스테이지 ────────────────────────────────────
# JRE가 아닌 JDK 사용: Java 문제 검증에 javac(컴파일러)가 필요.
# python3 / g++ : Python·C++ 문제 검증 실행에 필요.
FROM eclipse-temurin:17-jdk
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# 사용자 제출 코드는 서버(root)와 다른 권한 없는 계정으로 실행한다(ProcessUtils 참고).
# 서버와 같은 계정이면 /proc/1/environ 등으로 서버 환경변수(API 키)를 읽을 수 있다.
# 계정 전환(setpriv)·자원 제한(prlimit) 도구가 없으면 빌드를 실패시킨다.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin coderunner \
    && command -v setpriv && command -v prlimit
ENV CODE_RUNNER_USER=coderunner

# 서버의 "오늘"(연속 학습, 하루 목표, 생성 한도)이 한국 날짜 기준이 되도록 시간대를 고정한다.
# 기본값(UTC)이면 한국 시간 0~9시 사이의 풀이가 전날로 집계된다.
ENV TZ=Asia/Seoul

WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
