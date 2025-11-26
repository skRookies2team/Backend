# .env 파일을 읽어서 환경 변수로 설정하는 스크립트
# 사용법: . .\load-env.ps1

if (Test-Path .env) {
    Write-Host "📂 .env 파일 로드 중..." -ForegroundColor Cyan
    Write-Host ""

    Get-Content .env | ForEach-Object {
        # 빈 줄이나 주석(#으로 시작) 무시
        if ($_ -match '^\s*$' -or $_ -match '^\s*#') {
            return
        }

        # KEY=VALUE 형식 파싱
        if ($_ -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()

            # 환경 변수 설정
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')

            # 민감한 정보는 마스킹하여 출력
            if ($key -match '(PASSWORD|SECRET|KEY)') {
                $maskedValue = if ($value.Length -gt 4) {
                    $value.Substring(0, 4) + "***"
                } else {
                    "***"
                }
                Write-Host "  ✅ $key = $maskedValue" -ForegroundColor Green
            } else {
                Write-Host "  ✅ $key = $value" -ForegroundColor Green
            }
        }
    }

    # SPRING_PROFILES_ACTIVE가 없으면 dev로 설정
    if (-not $env:SPRING_PROFILES_ACTIVE) {
        $env:SPRING_PROFILES_ACTIVE = "dev"
        Write-Host "  ✅ SPRING_PROFILES_ACTIVE = dev" -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "✅ 환경 변수 로드 완료!" -ForegroundColor Green
    Write-Host ""
    Write-Host "이제 다음 명령어를 실행하세요:" -ForegroundColor Yellow
    Write-Host "  .\gradlew bootRun" -ForegroundColor White
    Write-Host "  또는" -ForegroundColor Gray
    Write-Host "  .\gradlew test --tests S3ServiceTest" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "❌ .env 파일을 찾을 수 없습니다!" -ForegroundColor Red
    Write-Host "먼저 .env.example을 복사하여 .env 파일을 만드세요:" -ForegroundColor Yellow
    Write-Host "  Copy-Item .env.example .env" -ForegroundColor White
    Write-Host "  notepad .env" -ForegroundColor White
}
