package com.besenior.harucoding.generation.verify;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 외부 프로세스(컴파일러/인터프리터) 실행 헬퍼.
 * stdout/stderr를 별도 스레드로 읽어 데드락을 피하고, 타임아웃 시 강제 종료한다.
 */
final class ProcessUtils {

    record Result(int exit, String stdout, String stderr, boolean timedOut) {}

    private ProcessUtils() {}

    static Result run(List<String> command, File workDir, int timeoutSec)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) pb.directory(workDir);
        Process process = pb.start();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> out = pool.submit(() -> readAll(process.getInputStream()));
            Future<String> err = pool.submit(() -> readAll(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new Result(-1, get(out), get(err), true);
            }
            return new Result(process.exitValue(), get(out), get(err), false);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String readAll(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
        } catch (IOException ignored) {
            // 스트림 종료 시 무시
        }
        return sb.toString();
    }

    private static String get(Future<String> f) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }
}
