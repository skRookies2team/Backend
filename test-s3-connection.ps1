# S3 연결 테스트 스크립트
# 실행: .\test-s3-connection.ps1

Write-Host "================================" -ForegroundColor Cyan
Write-Host "S3 연결 테스트 시작" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# .env 파일 확인
if (-Not (Test-Path .env)) {
    Write-Host "❌ .env 파일이 없습니다!" -ForegroundColor Red
    Write-Host "   먼저 setup-s3.ps1을 실행하세요" -ForegroundColor Yellow
    exit 1
}

# .env 파일 읽기
Get-Content .env | ForEach-Object {
    if ($_ -match '^AWS_') {
        $key = $_.Split('=')[0]
        $value = $_.Split('=')[1]

        if ($value -match '^(AKIA\.\.\.|\.\.\.|\s*)$') {
            Write-Host "⚠️  $key 값이 설정되지 않았습니다" -ForegroundColor Yellow
        } else {
            $masked = $value.Substring(0, [Math]::Min(4, $value.Length)) + "***"
            Write-Host "✅ $key = $masked" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Mock 테스트 실행 (AWS 연결 불필요)" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

.\gradlew test --tests S3ServiceMockTest

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "실제 S3 연결 테스트" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host "주의: 실제 AWS에 연결하여 약간의 비용이 발생할 수 있습니다" -ForegroundColor Yellow
Write-Host ""

$response = Read-Host "실제 S3 테스트를 진행하시겠습니까? (Y/N)"
if ($response -eq 'Y' -or $response -eq 'y') {
    Write-Host "S3 통합 테스트 실행 중..." -ForegroundColor Cyan
    .\gradlew test --tests S3ServiceTest -Dspring.profiles.active=dev
} else {
    Write-Host "테스트를 건너뜁니다" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "테스트 완료!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Cyan
