Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Starting SmartHire - Resume Intelligence & Job Portal" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

javac -cp "lib/*;src" -d bin src/*.java
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "[INFO] Compilation successful. Starting WebServer on http://localhost:8080 ..." -ForegroundColor Green
Write-Host ""
java -cp "lib/*;bin" WebServer
