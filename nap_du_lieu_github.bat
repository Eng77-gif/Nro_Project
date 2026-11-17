@echo off
rem Nap sql\team2026.sql.enc (tai tu GitHub) de len database may nay. Tat server truoc, can backup.key dung mat khau.
cd /d "%~dp0"
git pull
java -cp 20.jar nro.models.data.SqlBackup import
pause
