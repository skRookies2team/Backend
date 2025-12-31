package com.story.game.achievement.config;

import com.story.game.achievement.entity.Achievement;
import com.story.game.achievement.repository.AchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementInitializer implements ApplicationRunner {

    private final AchievementRepository achievementRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Initializing achievements...");

        // 이미 업적이 존재하면 초기화하지 않음
        if (achievementRepository.count() > 0) {
            log.info("Achievements already exist. Skipping initialization.");
            return;
        }

        List<Achievement> achievements = List.of(
                // 플레이 관련 업적
                Achievement.builder()
                        .code("FIRST_PLAY")
                        .name("첫 발걸음")
                        .description("첫 번째 스토리를 플레이하세요")
                        .type(Achievement.AchievementType.PLAY_COUNT)
                        .targetValue(1)
                        .points(10)
                        .build(),

                Achievement.builder()
                        .code("PLAY_10")
                        .name("열정적인 독자")
                        .description("스토리를 10번 플레이하세요")
                        .type(Achievement.AchievementType.PLAY_COUNT)
                        .targetValue(10)
                        .points(50)
                        .build(),

                Achievement.builder()
                        .code("PLAY_50")
                        .name("스토리 마스터")
                        .description("스토리를 50번 플레이하세요")
                        .type(Achievement.AchievementType.PLAY_COUNT)
                        .targetValue(50)
                        .points(100)
                        .build(),

                // 완료 관련 업적
                Achievement.builder()
                        .code("FIRST_COMPLETION")
                        .name("완주의 기쁨")
                        .description("첫 번째 스토리를 완료하세요")
                        .type(Achievement.AchievementType.COMPLETION_COUNT)
                        .targetValue(1)
                        .points(20)
                        .build(),

                Achievement.builder()
                        .code("COMPLETE_10")
                        .name("끈기있는 플레이어")
                        .description("스토리를 10개 완료하세요")
                        .type(Achievement.AchievementType.COMPLETION_COUNT)
                        .targetValue(10)
                        .points(100)
                        .build(),

                // 엔딩 관련 업적
                Achievement.builder()
                        .code("FIRST_ENDING")
                        .name("첫 번째 결말")
                        .description("첫 번째 엔딩을 달성하세요")
                        .type(Achievement.AchievementType.ENDING_COUNT)
                        .targetValue(1)
                        .points(15)
                        .build(),

                Achievement.builder()
                        .code("ENDING_COLLECTOR")
                        .name("엔딩 컬렉터")
                        .description("20개의 서로 다른 엔딩을 달성하세요")
                        .type(Achievement.AchievementType.ENDING_COUNT)
                        .targetValue(20)
                        .points(150)
                        .build(),

                // 창작 관련 업적
                Achievement.builder()
                        .code("FIRST_CREATION")
                        .name("창작의 시작")
                        .description("첫 번째 스토리를 생성하세요")
                        .type(Achievement.AchievementType.CREATION_COUNT)
                        .targetValue(1)
                        .points(30)
                        .build(),

                Achievement.builder()
                        .code("CREATOR")
                        .name("스토리 크리에이터")
                        .description("스토리를 5개 생성하세요")
                        .type(Achievement.AchievementType.CREATION_COUNT)
                        .targetValue(5)
                        .points(100)
                        .build(),

                Achievement.builder()
                        .code("MASTER_CREATOR")
                        .name("마스터 크리에이터")
                        .description("스토리를 10개 생성하세요")
                        .type(Achievement.AchievementType.CREATION_COUNT)
                        .targetValue(10)
                        .points(200)
                        .build(),

                // 커뮤니티 관련 업적
                Achievement.builder()
                        .code("FIRST_POST")
                        .name("커뮤니티의 일원")
                        .description("첫 번째 게시글을 작성하세요")
                        .type(Achievement.AchievementType.POST_COUNT)
                        .targetValue(1)
                        .points(10)
                        .build(),

                Achievement.builder()
                        .code("ACTIVE_MEMBER")
                        .name("활발한 활동가")
                        .description("게시글을 10개 작성하세요")
                        .type(Achievement.AchievementType.POST_COUNT)
                        .targetValue(10)
                        .points(50)
                        .build()
        );

        achievementRepository.saveAll(achievements);
        log.info("Successfully initialized {} achievements", achievements.size());
    }
}
