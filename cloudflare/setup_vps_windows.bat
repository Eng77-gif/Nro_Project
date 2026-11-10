@echo off
chcp 65001 >nul
rem Thiet lap VPS Windows cho trang chu tren Cloudflare: mo cong 8080 + ghi khoa web.proxy.key vao Config.properties.
rem Dat file nay trong thu muc server (cung cho Config.properties), chuot phai -> Run as administrator.
cd /d "%~dp0"
net session >nul 2>&1 || (echo Hay chuot phai file nay va chon "Run as administrator". & pause & exit /b 1)
if not exist Config.properties (echo Khong thay Config.properties. Hay dat file nay trong thu muc server. & pause & exit /b 1)
set "KEY="
if "%KEY%"=="" set /p KEY=Dan khoa PROXY_KEY roi bam Enter: 
netsh advfirewall firewall delete rule name="NRO Web 8080" >nul 2>&1
netsh advfirewall firewall add rule name="NRO Web 8080" dir=in action=allow protocol=TCP localport=8080 >nul && echo [OK] Da mo cong 8080 tren tuong lua Windows
powershell -NoProfile -ExecutionPolicy Bypass -Command "$f='Config.properties'; $t=[IO.File]::ReadAllText($f); foreach($kv in @(@('web.enable','true'),@('web.host','0.0.0.0'),@('web.port','8080'),@('web.proxy.key','%KEY%'))){ $k=[regex]::Escape($kv[0]); if($t -match ('(?m)^'+$k+'=.*$')){ $t=[regex]::Replace($t,('(?m)^'+$k+'=.*$'),($kv[0]+'='+$kv[1])) } else { $t=$t.TrimEnd()+[Environment]::NewLine+$kv[0]+'='+$kv[1]+[Environment]::NewLine } }; [IO.File]::WriteAllText($f,$t,(New-Object Text.UTF8Encoding $false))" && echo [OK] Da ghi web.proxy.key vao Config.properties
echo.
echo Xong. Khoi dong lai server game (tat cua so server roi chay lai run.bat / run_vps_yeu.bat).
echo Neu nha cung cap VPS co tuong lua rieng tren trang quan ly, mo them cong TCP 8080 o do.
pause
