@echo off
chcp 65001 >nul
rem Quen mat khau du lieu GitHub: xem mat khau dang luu (backup.key) hoac dat mat khau moi roi ma hoa lai database may nay + day len GitHub.
cd /d "%~dp0"
if exist backup.key (
  echo Mat khau dang luu tren may nay:
  type backup.key
  echo.
)
echo Khong nho mat khau thi ban tren GitHub khong giai ma duoc nua.
echo Dat mat khau moi: ma hoa lai database DANG CHAY tren may nay roi day de len GitHub.
set /p OK=Dat mat khau moi? (y/n): 
if /i not "%OK%"=="y" goto :eof
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$p=Read-Host 'Mat khau moi (tu 6 ky tu)' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"`) do set NEWPW=%%p
if "%NEWPW%"=="" (echo Huy. & pause & goto :eof)
if "%NEWPW:~5,1%"=="" (echo Mat khau qua ngan. & pause & goto :eof)
if exist backup.key copy /y backup.key backup.key.old >nul
powershell -NoProfile -Command "[IO.File]::WriteAllText('backup.key', $env:NEWPW + [Environment]::NewLine)"
set NEWPW=
java -cp 20.jar nro.models.data.SqlBackup export push
if errorlevel 1 (
  if exist backup.key.old move /y backup.key.old backup.key >nul
  echo Chua day duoc len GitHub. Da giu lai mat khau cu.
) else (
  echo Xong. May khac dat mat khau moi nay vao backup.key de nap.
)
pause
