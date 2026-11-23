@echo off
chcp 65001 >nul
rem Cap nhat ban moi tu GitHub (VPS Windows): khong tai cac file .md va file chi dung cho Termux.
cd /d "%~dp0"
where git >nul 2>nul || (echo Chua cai git. & pause & goto :eof)
if not exist .git (echo Thu muc nay khong phai repo git. & pause & goto :eof)
rem bo qua .md va file Termux (sparse-checkout); lan sau cap nhat van giu cai dat nay
git config core.sparseCheckout true
git sparse-checkout init --no-cone >nul 2>nul
(
  echo /*
  echo !*.md
  echo !/nro_termux.sh
  echo !/run_termux.sh
  echo !/termux_install.sh
  echo !/nro-ui.sh
) > .git\info\sparse-checkout
git pull --ff-only
git read-tree -mu HEAD
echo.
echo Da cap nhat. Khoi dong lai server de dung ban moi.
pause
