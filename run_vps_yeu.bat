@ECHO OFF
REM Console UTF-8: chu tieng Viet trong log hien dung
chcp 65001 >nul
title Ngọc Rồng Online - Máy chủ game
REM Chạy server cho VPS yếu (1-2 nhân, 1-2 GB RAM). VPS 1 GB: 320m, VPS 2 GB: 448m.
IF "%RAM_MAX%"=="" SET RAM_MAX=384m
REM Bao tri (ma thoat 3): server tu bat lai ngay trong cua so nay, khong mo cua so moi.
SET NRO_RESTART_LOOP=1
:START
java -server -XX:-OmitStackTraceInFastThrow -Dfile.encoding=UTF-8 -Xms48m -Xmx%RAM_MAX% -XX:+UseG1GC -XX:G1PeriodicGCInterval=20000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=25 -XX:+UseStringDeduplication -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xss384k -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError -jar 20.jar
REM Ca khoi IF doc 1 lan truoc khi chay: cap_nhat ghi de chinh file nay van an toan, GOTO START doc lai ban moi.
IF %ERRORLEVEL% EQU 3 (
    echo.
    echo Bao tri xong. Kiem tra ban cap nhat moi tren GitHub...
    IF EXIST cap_nhat.bat call cap_nhat.bat auto
    echo Server tu khoi dong lai sau 5 phut nua - bam phim bat ky de bat lai ngay.
    timeout /t 300
    GOTO START
)
PAUSE
