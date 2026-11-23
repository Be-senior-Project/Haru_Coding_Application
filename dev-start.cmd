@echo off
setlocal enabledelayedexpansion

REM ===== HaruCoding dev environment launcher =====
REM Prerequisites: Docker Desktop running, Android emulator running

REM 1) Check Docker Desktop
docker info >nul 2>&1
if errorlevel 1 (
    echo [X] Docker Desktop is not running. Start it first.
    pause
    exit /b 1
)
echo [1/4] Docker OK

REM 2) Check Android emulator
adb devices | findstr /r "emulator" >nul 2>&1
if errorlevel 1 (
    echo [X] Android emulator is not running. Start it first.
    pause
    exit /b 1
)
echo [2/4] Emulator OK

REM 3) Start backend + DB, wait for port 8080 (max 3 min)
cd /d "%~dp0backend"
docker compose -p harucoding up -d --build
if errorlevel 1 (
    echo [X] Backend failed to start. See the message above. Check DB_PASSWORD and JWT_SECRET in backend\.env.
    pause
    exit /b 1
)
echo [3/4] Waiting for backend...

set /a tries=0
:waitloop
curl -sf -o nul http://localhost:8080/api/problems
if not errorlevel 1 goto backend_ready
set /a tries+=1
if !tries! geq 60 (
    echo [X] Backend not responding after 3 min. Check: docker compose -p harucoding logs api
    pause
    exit /b 1
)
timeout /t 3 /nobreak >nul
goto waitloop

:backend_ready
echo [3/4] Backend OK - http://localhost:8080

REM 4) Build and install the app (Metro starts automatically)
cd /d "%~dp0frontend"
echo [4/4] Building and launching the app...
call npx react-native run-android

pause
