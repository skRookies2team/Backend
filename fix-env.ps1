# .env 파일의 공백/줄바꿈 자동 제거 스크립트
# 사용법: .\fix-env.ps1

if (Test-Path .env) {
    Write-Host "🔧 .env 파일 정리 중..." -ForegroundColor Cyan

    $lines = Get-Content .env
    $cleanedLines = @()

    foreach ($line in $lines) {
        # 빈 줄이나 주석은 그대로 유지
        if ($line -match '^\s*$' -or $line -match '^\s*#') {
            $cleanedLines += $line
            continue
        }

        # KEY=VALUE 형식 정리
        if ($line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            # 따옴표 제거
            $value = $value.Trim('"').Trim("'")
            $cleanedLines += "$key=$value"
        }
    }

    # 파일 덮어쓰기
    $cleanedLines | Set-Content .env -Encoding UTF8 -NoNewline

    Write-Host "✅ .env 파일 정리 완료!" -ForegroundColor Green
    Write-Host ""
    Write-Host "이제 다음 명령어를 실행하세요:" -ForegroundColor Yellow
    Write-Host "  . .\load-env.ps1" -ForegroundColor White
} else {
    Write-Host "❌ .env 파일을 찾을 수 없습니다!" -ForegroundColor Red
}
