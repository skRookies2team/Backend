# AWS S3 설정 스크립트 (Windows PowerShell)
# 실행: .\setup-s3.ps1

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Story Game - S3 설정" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 1. .env 파일 생성
if (-Not (Test-Path .env)) {
    Copy-Item .env.example .env
    Write-Host "✅ .env 파일 생성 완료" -ForegroundColor Green
} else {
    Write-Host "⚠️  .env 파일이 이미 존재합니다" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "다음 단계:" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host "1. AWS Console에서 S3 버킷 생성" -ForegroundColor White
Write-Host "   → https://console.aws.amazon.com/s3/" -ForegroundColor Gray
Write-Host ""
Write-Host "2. IAM 사용자 생성 및 액세스 키 발급" -ForegroundColor White
Write-Host "   → https://console.aws.amazon.com/iam/" -ForegroundColor Gray
Write-Host ""
Write-Host "3. .env 파일에 AWS 자격증명 입력" -ForegroundColor White
Write-Host "   → notepad .env" -ForegroundColor Gray
Write-Host ""
Write-Host "4. 필수 항목:" -ForegroundColor Yellow
Write-Host "   - AWS_S3_BUCKET" -ForegroundColor Yellow
Write-Host "   - AWS_ACCESS_KEY" -ForegroundColor Yellow
Write-Host "   - AWS_SECRET_KEY" -ForegroundColor Yellow
Write-Host ""
Write-Host "5. 애플리케이션 실행" -ForegroundColor White
Write-Host "   → .\gradlew bootRun" -ForegroundColor Gray
Write-Host ""
Write-Host "================================" -ForegroundColor Cyan

# .env 파일 열기
$response = Read-Host "지금 .env 파일을 편집하시겠습니까? (Y/N)"
if ($response -eq 'Y' -or $response -eq 'y') {
    notepad .env
}
