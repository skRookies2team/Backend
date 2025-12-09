package com.story.game.gameplay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.ai.service.RelayServerClient;
import com.story.game.common.dto.FinalEndingDto;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.gameplay.dto.GameStateResponseDto;
import com.story.game.gameplay.entity.GameSession;
import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.story.entity.Episode;
import com.story.game.story.entity.EpisodeEnding;
import com.story.game.story.mapper.StoryMapper;
import com.story.game.story.repository.EpisodeEndingRepository;
import com.story.game.story.repository.EpisodeRepository;
import com.story.game.story.repository.StoryChoiceRepository;
import com.story.game.story.repository.StoryNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GameService 최종 엔딩 평가 로직 테스트
 *
 * 주요 테스트:
 * 1. endingConfigJson이 null일 때
 * 2. 게이지 조건이 매칭되는 엔딩 선택
 * 3. 조건 불일치 시 기본 엔딩 선택
 * 4. 빈 엔딩 목록 처리
 */
@ExtendWith(MockitoExtension.class)
class GameServiceEndingTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private StoryDataRepository storyDataRepository;

    @Mock
    private StoryCreationRepository storyCreationRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private StoryNodeRepository storyNodeRepository;

    @Mock
    private StoryChoiceRepository storyChoiceRepository;

    @Mock
    private EpisodeEndingRepository episodeEndingRepository;

    @Mock
    private RelayServerClient relayServerClient;

    @Mock
    private StoryMapper storyMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GameService gameService;

    private StoryCreation testStoryCreation;
    private GameSession testGameSession;

    @BeforeEach
    void setUp() {
        testStoryCreation = StoryCreation.builder()
                .id("story_test")
                .title("테스트 스토리")
                .status(StoryCreation.CreationStatus.COMPLETED)
                .storyDataId(1L)
                .build();

        testGameSession = GameSession.builder()
                .id("session_test")
                .storyDataId(1L)
                .currentEpisodeId(UUID.randomUUID().toString())
                .currentNodeId(UUID.randomUUID().toString())
                .gaugeStates(new HashMap<>())
                .accumulatedTags(new HashMap<>())
                .visitedNodes(new ArrayList<>())
                .completedEpisodes(new ArrayList<>())
                .isCompleted(false)
                .build();
    }

    @Test
    @DisplayName("endingConfigJson이 null일 때 - null 반환 및 에러 로그")
    void testEvaluateFinalEnding_NullConfig() throws Exception {
        // Given
        testStoryCreation.setEndingConfigJson(null);
        Map<String, Integer> gaugeStates = Map.of(
                "trust", 70,
                "courage", 50
        );

        // When
        FinalEndingDto result = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("게이지 조건이 매칭되는 첫 번째 엔딩 선택")
    void testEvaluateFinalEnding_FirstMatchingCondition() throws Exception {
        // Given
        List<FinalEndingDto> finalEndings = Arrays.asList(
                FinalEndingDto.builder()
                        .id("ending_1")
                        .title("해피 엔딩")
                        .condition("#trust >= 70 && #courage >= 60")
                        .summary("신뢰와 용기로 승리했습니다!")
                        .build(),
                FinalEndingDto.builder()
                        .id("ending_2")
                        .title("배드 엔딩")
                        .condition("#trust < 30")
                        .summary("신뢰를 잃었습니다...")
                        .build(),
                FinalEndingDto.builder()
                        .id("ending_default")
                        .title("일반 엔딩")
                        .condition("default")
                        .summary("평범한 결말")
                        .build()
        );

        testStoryCreation.setEndingConfigJson(objectMapper.writeValueAsString(finalEndings));

        Map<String, Integer> gaugeStates = Map.of(
                "trust", 75,
                "courage", 65
        );

        // When
        FinalEndingDto result = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("ending_1");
        assertThat(result.getTitle()).isEqualTo("해피 엔딩");
    }

    @Test
    @DisplayName("조건 불일치 시 마지막 엔딩(기본값) 선택")
    void testEvaluateFinalEnding_DefaultEnding() throws Exception {
        // Given
        List<FinalEndingDto> finalEndings = Arrays.asList(
                FinalEndingDto.builder()
                        .id("ending_1")
                        .title("해피 엔딩")
                        .condition("#trust >= 90")
                        .summary("완벽한 승리!")
                        .build(),
                FinalEndingDto.builder()
                        .id("ending_2")
                        .title("배드 엔딩")
                        .condition("#trust < 10")
                        .summary("완전한 실패...")
                        .build(),
                FinalEndingDto.builder()
                        .id("ending_default")
                        .title("일반 엔딩")
                        .condition("default")
                        .summary("평범한 결말")
                        .build()
        );

        testStoryCreation.setEndingConfigJson(objectMapper.writeValueAsString(finalEndings));

        Map<String, Integer> gaugeStates = Map.of(
                "trust", 50,  // 90 미만, 10 이상
                "courage", 50
        );

        // When
        FinalEndingDto result = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("ending_default");
        assertThat(result.getTitle()).isEqualTo("일반 엔딩");
    }

    @Test
    @DisplayName("빈 엔딩 목록일 때 - null 반환")
    void testEvaluateFinalEnding_EmptyList() throws Exception {
        // Given
        List<FinalEndingDto> finalEndings = Collections.emptyList();
        testStoryCreation.setEndingConfigJson(objectMapper.writeValueAsString(finalEndings));

        Map<String, Integer> gaugeStates = Map.of("trust", 50);

        // When
        FinalEndingDto result = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("복잡한 조건 평가 - AND/OR 조합")
    void testEvaluateFinalEnding_ComplexConditions() throws Exception {
        // Given
        List<FinalEndingDto> finalEndings = Arrays.asList(
                FinalEndingDto.builder()
                        .id("ending_1")
                        .title("최고 엔딩")
                        .condition("(#trust >= 80 AND #courage >= 70) OR #wisdom >= 90")
                        .summary("최고의 결말!")
                        .build(),
                FinalEndingDto.builder()
                        .id("ending_default")
                        .title("일반 엔딩")
                        .condition("default")
                        .summary("평범한 결말")
                        .build()
        );

        testStoryCreation.setEndingConfigJson(objectMapper.writeValueAsString(finalEndings));

        // Case 1: trust >= 80 AND courage >= 70
        Map<String, Integer> gaugeStates1 = Map.of(
                "trust", 85,
                "courage", 75,
                "wisdom", 30
        );

        // When
        FinalEndingDto result1 = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates1);

        // Then
        assertThat(result1).isNotNull();
        assertThat(result1.getId()).isEqualTo("ending_1");

        // Case 2: wisdom >= 90 (OR 조건)
        Map<String, Integer> gaugeStates2 = Map.of(
                "trust", 30,
                "courage", 30,
                "wisdom", 95
        );

        // When
        FinalEndingDto result2 = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates2);

        // Then
        assertThat(result2).isNotNull();
        assertThat(result2.getId()).isEqualTo("ending_1");
    }

    @Test
    @DisplayName("게임 종료 플로우 전체 테스트")
    void testHandleGameEnd_FullFlow() throws Exception {
        // Given
        List<FinalEndingDto> finalEndings = Arrays.asList(
                FinalEndingDto.builder()
                        .id("ending_victory")
                        .title("승리 엔딩")
                        .condition("#trust >= 60")
                        .summary("당신은 승리했습니다!")
                        .build(),
                FinalEndingDto.builder()
                        .id("ending_defeat")
                        .title("패배 엔딩")
                        .condition("default")
                        .summary("아쉬운 결말...")
                        .build()
        );

        testStoryCreation.setEndingConfigJson(objectMapper.writeValueAsString(finalEndings));

        testGameSession.setGaugeStates(Map.of("trust", 70, "courage", 50));
        testGameSession.setCompletedEpisodes(Arrays.asList("ep1", "ep2", "ep3"));

        when(gameSessionRepository.save(any(GameSession.class))).thenReturn(testGameSession);

        // When
        GameStateResponseDto result = invokeHandleGameEnd(
                testGameSession,
                testStoryCreation,
                null
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getIsGameEnd()).isTrue();
        assertThat(result.getFinalEnding()).isNotNull();
        assertThat(result.getFinalEnding().getId()).isEqualTo("ending_victory");
        assertThat(result.getFinalEnding().getTitle()).isEqualTo("승리 엔딩");
        assertThat(result.getEpisodeTitle()).isEqualTo("승리 엔딩");
        assertThat(result.getNodeText()).isEqualTo("당신은 승리했습니다!");

        // 세션 상태 검증
        verify(gameSessionRepository, times(1)).save(any(GameSession.class));
        assertThat(testGameSession.getIsCompleted()).isTrue();
        assertThat(testGameSession.getFinalEndingId()).isEqualTo("ending_victory");
    }

    @Test
    @DisplayName("endingConfigJson이 잘못된 JSON일 때")
    void testEvaluateFinalEnding_InvalidJson() throws Exception {
        // Given
        testStoryCreation.setEndingConfigJson("{invalid json}");
        Map<String, Integer> gaugeStates = Map.of("trust", 50);

        // When
        FinalEndingDto result = invokeEvaluateFinalEnding(testStoryCreation, gaugeStates);

        // Then
        assertThat(result).isNull();
    }

    // === Helper Methods ===

    /**
     * private 메서드 evaluateFinalEnding 호출 (리플렉션 사용)
     */
    private FinalEndingDto invokeEvaluateFinalEnding(
            StoryCreation storyCreation,
            Map<String, Integer> gaugeStates) throws Exception {

        return ReflectionTestUtils.invokeMethod(
                gameService,
                "evaluateFinalEnding",
                storyCreation,
                gaugeStates
        );
    }

    /**
     * private 메서드 handleGameEnd 호출 (리플렉션 사용)
     */
    private GameStateResponseDto invokeHandleGameEnd(
            GameSession session,
            StoryCreation storyCreation,
            EpisodeEnding lastEpisodeEnding) throws Exception {

        return ReflectionTestUtils.invokeMethod(
                gameService,
                "handleGameEnd",
                session,
                storyCreation,
                lastEpisodeEnding
        );
    }
}
