@echo off
chcp 65001 >nul
echo ========================================
echo 연결 상태 테스트 스크립트
echo ========================================
echo.

echo [1단계] Spring Boot 서버 상태 확인
curl -s http://localhost:8080/api/health
echo.
echo.

echo [2단계] 데이터베이스 연결 확인
curl -s http://localhost:8080/api/health/database
echo.
echo.

echo [3단계] Python AI 서버 연결 확인
curl -s http://localhost:8080/api/health/ai-server
echo.
echo.

echo [4단계] 샘플 스토리 업로드
curl -s -X POST "http://localhost:8080/api/game/stories?title=테스트스토리&description=샘플데이터" ^
  -H "Content-Type: application/json" ^
  -d @test-data/sample-story.json
echo.
echo.

echo [5단계] 게임 시작
echo 응답에서 sessionId를 확인하세요:
curl -s -X POST http://localhost:8080/api/game/start ^
  -H "Content-Type: application/json" ^
  -d "{\"storyDataId\": 1}"
echo.
echo.

echo ========================================
echo 테스트 완료!
echo ========================================
echo.
echo 다음 명령으로 게임을 진행할 수 있습니다:
echo curl -X POST http://localhost:8080/api/game/{sessionId}/choice -H "Content-Type: application/json" -d "{\"choiceIndex\": 0}"
echo.
pause
