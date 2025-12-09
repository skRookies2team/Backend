package com.story.game.gameplay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.FinalEndingDto;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.gameplay.dto.GameStateResponseDto;
import com.story.game.gameplay.entity.GameSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 엔딩 응답 형식 검증 테스트
 *
 * 프론트엔드 연동을 위한 응답 구조 확인
 */
class GameServiceEndingIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("최종 엔딩 응답 JSON 구조 확인")
    void testGameEndResponse_JsonStructure() throws Exception {
        // Given - 게임 종료 시 응답 데이터 구조 시뮬레이션
        FinalEndingDto finalEnding = FinalEndingDto.builder()
                .id("ending_victory")
                .type("HAPPY")
                .title("승리의 엔딩")
                .condition("#trust >= 70")
                .summary("당신은 모든 도전을 극복하고 승리했습니다! 용기와 신뢰가 길을 열었습니다.")
                .build();

        GameStateResponseDto response = GameStateResponseDto.builder()
                .sessionId("test_session_123")
                .currentEpisodeId("ep_final")
                .gaugeStates(Map.of("trust", 75, "courage", 80))
                .accumulatedTags(Map.of("heroic", 5, "wise", 3))

                // 엔딩 표시 필드
                .episodeTitle("승리의 엔딩")  // 프론트가 제목으로 사용
                .nodeText("당신은 모든 도전을 극복하고 승리했습니다! 용기와 신뢰가 길을 열었습니다.")  // 프론트가 본문으로 사용

                .choices(Collections.emptyList())
                .isEpisodeEnd(true)
                .isGameEnd(true)  // ⭐ 프론트가 엔딩 화면 판단에 사용
                .episodeEnding(null)
                .finalEnding(finalEnding)  // ⭐ 최종 엔딩 정보
                .build();

        // When - JSON 직렬화
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);

        System.out.println("=== 최종 엔딩 응답 JSON ===");
        System.out.println(json);
        System.out.println("=========================");

        // Then - 필수 필드 존재 확인
        assertThat(json).contains("\"isGameEnd\" : true");
        assertThat(json).contains("\"finalEnding\"");
        assertThat(json).contains("ending_victory");
        assertThat(json).contains("승리의 엔딩");
        assertThat(json).contains("당신은 모든 도전을 극복하고 승리했습니다");

        // 역직렬화 테스트
        GameStateResponseDto deserialized = objectMapper.readValue(json, GameStateResponseDto.class);
        assertThat(deserialized.getIsGameEnd()).isTrue();
        assertThat(deserialized.getFinalEnding()).isNotNull();
        assertThat(deserialized.getFinalEnding().getId()).isEqualTo("ending_victory");
    }

    @Test
    @DisplayName("프론트엔드 표시 필드 매핑 확인")
    void testGameEndResponse_FrontendFields() {
        // Given
        FinalEndingDto finalEnding = FinalEndingDto.builder()
                .id("ending_peace")
                .title("평화 엔딩")
                .summary("세상에 평화가 찾아왔습니다.")
                .build();

        GameStateResponseDto response = GameStateResponseDto.builder()
                .sessionId("session_test")
                .isGameEnd(true)
                .finalEnding(finalEnding)

                // ⭐ 핵심: 이 필드들이 프론트엔드에서 실제로 표시되는 내용
                .episodeTitle("평화 엔딩")  // finalEnding.title 복사
                .nodeText("세상에 평화가 찾아왔습니다.")  // finalEnding.summary 복사
                .build();

        // Then
        assertThat(response.getEpisodeTitle()).isEqualTo(finalEnding.getTitle());
        assertThat(response.getNodeText()).isEqualTo(finalEnding.getSummary());
        assertThat(response.getFinalEnding()).isNotNull();
    }

    @Test
    @DisplayName("엔딩 미선택 시 기본 텍스트 표시")
    void testGameEndResponse_DefaultText() {
        // Given - finalEnding이 null인 경우 (조건 불일치)
        GameStateResponseDto response = GameStateResponseDto.builder()
                .sessionId("session_test")
                .isGameEnd(true)
                .finalEnding(null)  // 엔딩 미선택

                // 기본 텍스트 (GameService.handleGameEndResponse 참조)
                .episodeTitle("THE END")
                .nodeText("이야기가 끝났습니다.")
                .build();

        // Then
        assertThat(response.getEpisodeTitle()).isEqualTo("THE END");
        assertThat(response.getNodeText()).isEqualTo("이야기가 끝났습니다.");
        assertThat(response.getFinalEnding()).isNull();
    }

    @Test
    @DisplayName("프론트엔드 확인 체크리스트")
    void testFrontendIntegrationChecklist() {
        System.out.println("\n=== 프론트엔드 연동 체크리스트 ===");
        System.out.println("1. isGameEnd가 true일 때 엔딩 화면 표시 확인");
        System.out.println("2. finalEnding 객체 존재 여부 확인");
        System.out.println("3. finalEnding.title 또는 episodeTitle 표시 확인");
        System.out.println("4. finalEnding.summary 또는 nodeText 표시 확인");
        System.out.println("5. finalEnding.type으로 엔딩 분류 (HAPPY/BAD/NEUTRAL 등) 확인");
        System.out.println("\n=== 백엔드 응답 필드 ===");
        System.out.println("- isGameEnd: 게임 종료 플래그");
        System.out.println("- finalEnding: 최종 엔딩 상세 정보");
        System.out.println("- episodeTitle: 엔딩 제목 (finalEnding.title 복사)");
        System.out.println("- nodeText: 엔딩 본문 (finalEnding.summary 복사)");
        System.out.println("============================\n");
    }

    @Test
    @DisplayName("실제 API 응답 예시 생성")
    void testGenerateApiResponseExample() throws Exception {
        // 해피 엔딩
        FinalEndingDto happyEnding = FinalEndingDto.builder()
                .id("ending_happy")
                .type("HAPPY")
                .title("행복한 결말")
                .condition("#trust >= 70 AND #courage >= 60")
                .summary("당신의 용기와 신뢰가 모두를 구했습니다. 세상은 다시 평화를 되찾았고, 당신은 영웅으로 기억될 것입니다.")
                .build();

        GameStateResponseDto happyResponse = GameStateResponseDto.builder()
                .sessionId("session_abc123")
                .currentEpisodeId(null)
                .currentNodeId(null)
                .gaugeStates(Map.of("trust", 75, "courage", 65))
                .accumulatedTags(Map.of())
                .episodeTitle("행복한 결말")
                .nodeText("당신의 용기와 신뢰가 모두를 구했습니다. 세상은 다시 평화를 되찾았고, 당신은 영웅으로 기억될 것입니다.")
                .choices(Collections.emptyList())
                .isEpisodeEnd(true)
                .isGameEnd(true)
                .episodeEnding(null)
                .finalEnding(happyEnding)
                .build();

        String happyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(happyResponse);

        // 배드 엔딩
        FinalEndingDto badEnding = FinalEndingDto.builder()
                .id("ending_bad")
                .type("BAD")
                .title("슬픈 결말")
                .condition("#trust < 30")
                .summary("신뢰를 잃은 당신은 홀로 남겨졌습니다. 어둠이 세상을 뒤덮었습니다...")
                .build();

        GameStateResponseDto badResponse = GameStateResponseDto.builder()
                .sessionId("session_xyz789")
                .gaugeStates(Map.of("trust", 20, "courage", 40))
                .episodeTitle("슬픈 결말")
                .nodeText("신뢰를 잃은 당신은 홀로 남겨졌습니다. 어둠이 세상을 뒤덮었습니다...")
                .choices(Collections.emptyList())
                .isEpisodeEnd(true)
                .isGameEnd(true)
                .finalEnding(badEnding)
                .build();

        String badJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(badResponse);

        System.out.println("\n=== 해피 엔딩 API 응답 예시 ===");
        System.out.println(happyJson);
        System.out.println("\n=== 배드 엔딩 API 응답 예시 ===");
        System.out.println(badJson);

        // 검증
        assertThat(happyJson).contains("\"isGameEnd\" : true");
        assertThat(badJson).contains("\"isGameEnd\" : true");
    }
}
