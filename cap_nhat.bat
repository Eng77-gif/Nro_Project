@echo off
rem Cap nhat ban moi, khong can cai git. Tat server truoc khi chay.
cd /d "%~dp0"
mode con cols=100 >nul 2>nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0cap_nhat.ps1"
if errorlevel 1 (
  echo.
  echo Cap nhat loi. Kiem tra mang, hoac tat server truoc ^(20.jar dang chay khong ghi de duoc^).
) else (
  echo Xong. Khoi dong lai server neu vua cap nhat.
)
pause
