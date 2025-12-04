package com.story.game.infrastructure.config;

import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.creation.service.SequentialGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "Health Check", description = "서버 및 연결 상태 확인 API")
public class HealthCheckController {

    private final DataSource dataSource;
    private final StoryDataRepository storyDataRepository;
    private final GameSessionRepository gameSessionRepository;
    private final SequentialGenerationService sequentialGenerationService;

    @GetMapping
    @Operation(summary = "전체 시스템 상태 확인", description = "서버, 데이터베이스, AI 서버의 연결 상태를 확인합니다")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new HashMap<>();

        // Spring Boot 서버 상태
        status.put("server", "UP");

        // 데이터베이스 연결 확인
        boolean dbConnected = checkDatabaseConnection();
        status.put("database", dbConnected ? "UP" : "DOWN");

        // AI 서버 연결 확인
        boolean aiConnected = sequentialGenerationService.checkAiServerHealth();
        status.put("aiServer", aiConnected ? "UP" : "DOWN");

        // 전체 상태
        boolean allHealthy = dbConnected && aiConnected;
        status.put("status", allHealthy ? "HEALTHY" : "DEGRADED");

        return ResponseEntity.ok(status);
    }

    @GetMapping("/database")
    @Operation(summary = "데이터베이스 상태 확인", description = "데이터베이스 연결 상태 및 저장된 데이터 개수를 확인합니다")
    public ResponseEntity<Map<String, Object>> databaseStatus() {
        Map<String, Object> dbStatus = new HashMap<>();

        try {
            // 연결 테스트
            Connection connection = dataSource.getConnection();
            dbStatus.put("connected", true);
            dbStatus.put("catalog", connection.getCatalog());
            connection.close();

            // 데이터 확인
            long storyCount = storyDataRepository.count();
            long sessionCount = gameSessionRepository.count();

            dbStatus.put("storyCount", storyCount);
            dbStatus.put("sessionCount", sessionCount);
            dbStatus.put("status", "UP");

        } catch (Exception e) {
            dbStatus.put("connected", false);
            dbStatus.put("error", e.getMessage());
            dbStatus.put("status", "DOWN");
        }

        return ResponseEntity.ok(dbStatus);
    }

    @GetMapping("/ai-server")
    @Operation(summary = "AI 서버 상태 확인", description = "Python AI 서버의 연결 상태를 확인합니다")
    public ResponseEntity<Map<String, Object>> aiServerStatus() {
        Map<String, Object> aiStatus = new HashMap<>();

        boolean isHealthy = sequentialGenerationService.checkAiServerHealth();
        aiStatus.put("connected", isHealthy);
        aiStatus.put("status", isHealthy ? "UP" : "DOWN");

        if (!isHealthy) {
            aiStatus.put("message", "AI 서버에 연결할 수 없습니다. Python 서버가 실행 중인지 확인하세요.");
        }

        return ResponseEntity.ok(aiStatus);
    }

    private boolean checkDatabaseConnection() {
        try {
            Connection connection = dataSource.getConnection();
            boolean isValid = connection.isValid(2);
            connection.close();
            return isValid;
        } catch (Exception e) {
            return false;
        }
    }
}
