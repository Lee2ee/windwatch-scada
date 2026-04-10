package com.windwatch.scada.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot 구동 시 Python FastAPI 마이크로서비스를 자동으로 실행합니다.
 * - Vision API (YOLOv8)  : python/vision/vision_api.py  → port 8001
 * - LLM API (LangChain)  : python/llm/llm_api.py        → port 8002
 *
 * application.yml 에서 windwatch.python.auto-start=false 로 비활성화 가능.
 */
@Component
public class PythonServiceLauncher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonServiceLauncher.class);

    @Value("${windwatch.python.auto-start:true}")
    private boolean autoStart;

    private final List<Process> processes = new ArrayList<>();

    @Override
    public void run(ApplicationArguments args) {
        if (!autoStart) {
            log.info("[Python] auto-start disabled (windwatch.python.auto-start=false)");
            return;
        }

        Path root = Paths.get("").toAbsolutePath();
        log.info("[Python] Project root: {}", root);

        String python = findPython();
        if (python == null) {
            log.warn("[Python] Python executable not found in PATH. Services will not start.");
            return;
        }
        log.info("[Python] Using Python executable: {}", python);

        launchService("Vision", root.resolve("python/vision"), "vision_api.py", 8001, python);
        launchService("LLM",    root.resolve("python/llm"),    "llm_api.py",    8002, python);
    }

    // -------------------------------------------------------------------------

    private void launchService(String name, Path workDir, String script, int port, String python) {
        if (isPortOpen(port)) {
            log.info("[Python] {} already running on port {} — skipping launch.", name, port);
            return;
        }

        if (!workDir.resolve(script).toFile().exists()) {
            log.warn("[Python] Script not found: {}. Skipping {} service.", workDir.resolve(script), name);
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(python, script);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);   // stderr → stdout
            // Python이 UTF-8로 출력하도록 강제 (Windows 기본값 CP949 방지)
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");

            Process process = pb.start();
            processes.add(process);

            // 로그 스트리밍 (데몬 스레드) — UTF-8로 읽기
            String logPrefix = "[Python/" + name + "]";
            Thread logThread = new Thread(() -> {
                try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(
                                 process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("{} {}", logPrefix, line);
                    }
                } catch (IOException ignored) { /* process ended */ }
            }, "py-" + name.toLowerCase() + "-log");
            logThread.setDaemon(true);
            logThread.start();

            log.info("[Python] {} service starting on port {}...", name, port);
            boolean ready = waitForPort(port, 8_000);

            if (ready) {
                log.info("[Python] {} service is UP on port {}.", name, port);
            } else {
                log.warn("[Python] {} service did not respond within 8 s on port {}. " +
                         "Check Python dependencies (pip install fastapi uvicorn).", name, port);
            }

        } catch (IOException e) {
            log.error("[Python] Failed to launch {} service: {}", name, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    /** 포트가 열려 있으면(서버가 리스닝 중이면) true. */
    private boolean isPortOpen(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", port), 400);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 포트가 열릴 때까지 최대 timeoutMs 동안 대기. */
    private boolean waitForPort(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(port)) return true;
            try { Thread.sleep(400); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 가상환경(venv) → 시스템 Python 순서로 탐색하여 실행 파일 경로를 반환.
     * Windows: venv/Scripts/python.exe
     * Unix:    venv/bin/python
     */
    private String findPython() {
        Path root = Paths.get("").toAbsolutePath();

        // 가상환경 후보 (프로젝트 루트 기준)
        List<Path> venvCandidates = List.of(
            root.resolve("venv/Scripts/python.exe"),   // Windows
            root.resolve("venv/bin/python"),            // Unix
            root.resolve(".venv/Scripts/python.exe"),
            root.resolve(".venv/bin/python")
        );

        for (Path venvPython : venvCandidates) {
            if (venvPython.toFile().exists()) {
                log.info("[Python] Found venv Python: {}", venvPython);
                return venvPython.toAbsolutePath().toString();
            }
        }

        // 시스템 Python 폴백
        for (String candidate : List.of("python", "python3")) {
            try {
                Process p = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) { /* not found */ }
        }
        return null;
    }

    /** Spring 컨텍스트 종료 시 Python 프로세스를 함께 종료. */
    @PreDestroy
    public void shutdown() {
        for (Process process : processes) {
            if (!process.isAlive()) continue;
            log.info("[Python] Stopping PID {} ...", process.pid());
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("[Python] Force-killing PID {}", process.pid());
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
