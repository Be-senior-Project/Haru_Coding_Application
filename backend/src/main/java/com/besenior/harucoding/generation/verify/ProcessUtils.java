package com.besenior.harucoding.generation.verify;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 외부 프로세스(컴파일러/인터프리터) 실행 헬퍼.
 * stdout/stderr를 별도 스레드로 읽어 데드락을 피하고, 타임아웃 시 강제 종료한다.
 *
 * 여기서 실행하는 코드는 사용자가 제출한 코드와 LLM이 만든 코드라 믿을 수 없다. 그래서
 * - 서버 환경변수(OpenAI 키, 암호화 키 등)를 넘기지 않고 실행에 필요한 값만 넘긴다.
 * - CODE_RUNNER_USER가 설정된 리눅스 컨테이너에서는 권한 없는 계정으로 바꿔(setpriv) 실행하고,
 *   프로세스 수·파일 크기·열린 파일 수를 제한한다(prlimit). 서버와 같은 계정으로 돌리면
 *   /proc/1/environ 등으로 서버의 비밀값을 읽을 수 있기 때문이다.
 */
final class ProcessUtils {

    record Result(int exit, String stdout, String stderr, boolean timedOut) {}

    /** 격리 실행에 쓸 계정 이름을 담는 환경변수. 도커 이미지에서 설정한다. */
    static final String RUNNER_USER_ENV = "CODE_RUNNER_USER";

    /** 출력은 이 길이까지만 보관한다. 무한 출력으로 서버 메모리를 채우는 것을 막는다. */
    static final int MAX_OUTPUT_CHARS = 64 * 1024;
    static final String TRUNCATED_MARK = "\n...(출력이 너무 길어 잘렸습니다)";

    // 격리 계정 전체에 걸리는 한도. 스레드도 프로세스 수에 포함되므로, 여러 사용자가 동시에
    // Java 코드를 실행해도 모자라지 않을 만큼 두되 포크 폭탄은 막을 수 있는 값으로 둔다.
    private static final int LIMIT_NPROC = 512;
    private static final long LIMIT_FSIZE_BYTES = 16L * 1024 * 1024;
    private static final int LIMIT_NOFILE = 256;

    /** 자식 프로세스에 그대로 넘겨도 되는 환경변수(대소문자 무시). 나머지는 비밀값일 수 있어 버린다. */
    private static final Set<String> PASS_THROUGH_ENV = Set.of(
            "PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "COMSPEC", "JAVA_HOME");

    /** 격리 도구(setpriv·prlimit)를 쓸 수 있는지는 한 번만 확인한다. */
    private static volatile Boolean sandboxAvailable;

    private ProcessUtils() {}

    static Result run(List<String> command, File workDir, int timeoutSec)
            throws IOException, InterruptedException {
        String runnerUser = blankToNull(System.getenv(RUNNER_USER_ENV));
        if (runnerUser != null) {
            requireSandbox(runnerUser);
            if (workDir != null) {
                grantWorkDir(workDir.toPath(), runnerUser);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(sandboxed(command, runnerUser));
        if (workDir != null) pb.directory(workDir);
        Map<String, String> env = pb.environment();
        Map<String, String> childEnv = childEnvironment(System.getenv(), workDir);
        env.clear();
        env.putAll(childEnv);
        Process process = pb.start();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> out = pool.submit(() -> readCapped(process.getInputStream(), MAX_OUTPUT_CHARS));
            Future<String> err = pool.submit(() -> readCapped(process.getErrorStream(), MAX_OUTPUT_CHARS));

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                // 사용자 코드가 띄운 하위 프로세스까지 같이 정리한다(남겨두면 계속 자원을 쓴다)
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new Result(-1, get(out), get(err), true);
            }
            return new Result(process.exitValue(), get(out), get(err), false);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 자식 프로세스 환경변수. 허용 목록만 복사하고, 홈·임시 폴더는 작업 폴더로 돌린다.
     * 인코딩을 UTF-8로 고정해야 한글이 든 출력이 기대 출력과 같은 문자열로 비교된다.
     */
    static Map<String, String> childEnvironment(Map<String, String> parentEnv, File workDir) {
        Map<String, String> env = new HashMap<>();
        for (Map.Entry<String, String> e : parentEnv.entrySet()) {
            if (PASS_THROUGH_ENV.contains(e.getKey().toUpperCase(Locale.ROOT))) {
                env.put(e.getKey(), e.getValue());
            }
        }
        String tmp = workDir != null ? workDir.getAbsolutePath() : System.getProperty("java.io.tmpdir");
        env.put("HOME", tmp);
        env.put("TMPDIR", tmp);
        env.put("TEMP", tmp);
        env.put("TMP", tmp);
        env.put("LANG", "C.UTF-8");
        env.put("PYTHONIOENCODING", "utf-8");
        return env;
    }

    /** runnerUser가 있으면 prlimit(자원 제한) + setpriv(계정 전환)로 감싼 명령을, 없으면 원래 명령을 돌려준다. */
    static List<String> sandboxed(List<String> command, String runnerUser) {
        if (runnerUser == null) {
            return command;
        }
        List<String> wrapped = new ArrayList<>(List.of(
                "prlimit",
                "--nproc=" + LIMIT_NPROC,
                "--fsize=" + LIMIT_FSIZE_BYTES,
                "--nofile=" + LIMIT_NOFILE,
                "--",
                "setpriv",
                "--reuid=" + runnerUser,
                "--regid=" + runnerUser,
                "--clear-groups",
                "--no-new-privs",
                "--"));
        wrapped.addAll(command);
        return wrapped;
    }

    /** 스트림을 끝까지 읽되 maxChars까지만 보관한다. 나머지도 읽어 버려야 자식 프로세스가 출력에서 막히지 않는다. */
    static String readCapped(InputStream in, int maxChars) {
        StringBuilder sb = new StringBuilder();
        boolean truncated = false;
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) >= 0) {
                int room = maxChars - sb.length();
                if (n > room) {
                    truncated = true;
                }
                if (room > 0) {
                    sb.append(buf, 0, Math.min(n, room));
                }
            }
        } catch (IOException ignored) {
            // 스트림 종료 시 무시
        }
        if (truncated) {
            sb.append(TRUNCATED_MARK);
        }
        return sb.toString();
    }

    /**
     * 격리 실행을 쓰도록 설정됐는데 할 수 없으면 실행 자체를 거부한다.
     * 조용히 일반 실행으로 넘어가면 운영 서버에서 비밀값이 노출되는 상태를 모르고 지나치게 된다.
     */
    private static void requireSandbox(String runnerUser) {
        Boolean available = sandboxAvailable;
        if (available == null) {
            available = isLinux() && "root".equals(System.getProperty("user.name"))
                    && onPath("setpriv") && onPath("prlimit");
            sandboxAvailable = available;
        }
        if (!available) {
            throw new IllegalStateException(RUNNER_USER_ENV + "=" + runnerUser
                    + " 로 설정됐지만 격리 실행을 할 수 없습니다 (리눅스 root 실행, setpriv·prlimit 필요)");
        }
    }

    /** 격리 계정이 작업 폴더에 컴파일 결과를 쓸 수 있도록 폴더 소유자를 바꾼다. */
    private static void grantWorkDir(Path dir, String runnerUser) throws IOException {
        UserPrincipal owner = dir.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(runnerUser);
        Files.setOwner(dir, owner);
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean onPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            try {
                if (Files.isExecutable(Path.of(dir, executable))) {
                    return true;
                }
            } catch (InvalidPathException ignored) {
                // PATH에 잘못된 항목이 섞여 있어도 나머지 항목은 계속 확인한다
            }
        }
        return false;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    private static String get(Future<String> f) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }
}
