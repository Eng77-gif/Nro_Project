@echo off
chcp 65001 >nul
rem Cap nhat ban moi thang tu GitHub, khong can cai git.
rem Tai ZIP nhanh main, chep de len thu muc nay; bo qua file .md, file Termux, Config.properties, backup.key.
cd /d "%~dp0"
set REPO=https://github.com/Eng77-gif/Nro_Project/archive/refs/heads/main.zip
echo Dang tai ban moi tu GitHub...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "$zip=Join-Path $env:TEMP 'nro_update.zip'; $tmp=Join-Path $env:TEMP 'nro_update';" ^
  "if(Test-Path $tmp){Remove-Item $tmp -Recurse -Force};" ^
  "Invoke-WebRequest -Uri $env:REPO -OutFile $zip -UseBasicParsing;" ^
  "Expand-Archive -Path $zip -DestinationPath $tmp -Force;" ^
  "$src=(Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName;" ^
  "$skip=@('nro_termux.sh','run_termux.sh','termux_install.sh','nro-ui.sh','Config.properties','backup.key');" ^
  "$n=0; Get-ChildItem $src -Recurse -File | ForEach-Object {" ^
  "  $rel=$_.FullName.Substring($src.Length+1);" ^
  "  if($_.Extension -eq '.md' -or $skip -contains $_.Name){return};" ^
  "  $dst=Join-Path (Get-Location) $rel; New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null;" ^
  "  Copy-Item $_.FullName $dst -Force; $n++ };" ^
  "Remove-Item $zip,$tmp -Recurse -Force; Write-Host ('Da cap nhat ' + $n + ' file.')"
if errorlevel 1 (
  echo Cap nhat loi. Kiem tra mang hoac tat server truoc khi cap nhat ^(20.jar dang chay se khong ghi de duoc^).
) else (
  echo Xong. Khoi dong lai server de dung ban moi.
)
pause
