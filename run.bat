@echo off
echo ============================================================
echo   Starting SmartHire - Resume Intelligence ^& Job Portal
echo ============================================================
echo.
javac -cp "lib/*;src" -d bin src/*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %errorlevel%
)
echo.
echo [INFO] Compilation successful. Starting WebServer on http://localhost:8080 ...
echo.
java -cp "lib/*;bin" WebServer
