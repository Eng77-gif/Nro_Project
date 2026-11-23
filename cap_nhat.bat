@echo off
chcp 65001 >nul
rem Cap nhat ban moi, khong can cai git. Bo qua file .md, file Termux, Config.properties, backup.key.
cd /d "%~dp0"
set R=Eng77-gif/Nro_Project
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "$sha=(Invoke-RestMethod -Uri ('https://api.github.com/repos/'+$env:R+'/commits/main') -Headers @{'User-Agent'='nro-update'}).sha; $short=$sha.Substring(0,9);" ^
  "$cur=''; if(Test-Path 'VERSION'){$cur=(Get-Content 'VERSION' -TotalCount 1).Trim()};" ^
  "Write-Host ('Ban dang dung: ' + $(if($cur){$cur}else{'chua ro'}) + '   Ban moi nhat: ' + $short);" ^
  "if($cur -and $short.StartsWith($cur.Substring(0,[Math]::Min(7,$cur.Length)))){Write-Host 'Da la ban moi nhat.'; exit 0};" ^
  "$zip=Join-Path $env:TEMP 'nro_update.zip'; $tmp=Join-Path $env:TEMP 'nro_update'; if(Test-Path $tmp){Remove-Item $tmp -Recurse -Force};" ^
  "Write-Host ('Dang tai ban ' + $short + ' ...');" ^
  "Invoke-WebRequest -Uri ('https://codeload.github.com/'+$env:R+'/zip/'+$sha) -OutFile $zip -UseBasicParsing;" ^
  "Expand-Archive -Path $zip -DestinationPath $tmp -Force; $src=(Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName;" ^
  "$skip=@('nro_termux.sh','run_termux.sh','termux_install.sh','nro-ui.sh','Config.properties','backup.key');" ^
  "$n=0; Get-ChildItem $src -Recurse -File | ForEach-Object { $rel=$_.FullName.Substring($src.Length+1);" ^
  "  if($_.Extension -eq '.md' -or $skip -contains $_.Name){return};" ^
  "  $dst=Join-Path (Get-Location) $rel; New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null; Copy-Item $_.FullName $dst -Force; $n++ };" ^
  "Set-Content 'VERSION' $short; Remove-Item $zip,$tmp -Recurse -Force; Write-Host ('Da cap nhat len ban ' + $short + ' (' + $n + ' file).')"
if errorlevel 1 (
  echo Cap nhat loi. Kiem tra mang, hoac tat server truoc ^(20.jar dang chay khong ghi de duoc^).
) else (
  echo Xong. Khoi dong lai server neu vua cap nhat.
)
pause
