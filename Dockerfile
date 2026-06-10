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

WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
