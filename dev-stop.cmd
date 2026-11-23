@echo off

REM ===== HaruCoding dev environment shutdown (DB data is kept) =====
cd /d "%~dp0backend"
docker compose -p harucoding down
echo Backend/DB containers stopped. Close the emulator and Metro window manually.
pause
