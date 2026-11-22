@echo off
setlocal

REM ===== HaruCoding DB reset - DELETES ALL DATA =====

echo.
echo  WARNING: This deletes the entire database volume.
echo  All accounts, generated problems, and solve records will be lost.
echo.
set /p CONFIRM="Type YES to continue: "
if /i not "%CONFIRM%"=="YES" (
    echo Cancelled.
    pause
    exit /b 0
)

cd /d "%~dp0backend"
docker compose -p harucoding down -v

echo.
echo DB volume removed.
echo Run dev-start.cmd to start with a fresh database.
pause