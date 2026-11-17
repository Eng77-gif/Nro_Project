@echo off
rem Day database (ma hoa bang backup.key) len GitHub ngay. Server tu lam viec nay sau moi lan bao tri.
cd /d "%~dp0"
java -cp 20.jar nro.models.data.SqlBackup export push
pause
