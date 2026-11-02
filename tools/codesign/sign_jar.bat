@echo off
rem Ký file jar bằng chữ ký số "Việt". Cách dùng: sign_jar.bat 20.jar viet-codesign.p12
chcp 65001 >nul
set JAR=%1
if "%JAR%"=="" set JAR=20.jar
set KS=%2
if "%KS%"=="" set KS=viet-codesign.p12
jarsigner -J-Dfile.encoding=UTF-8 -keystore "%KS%" -storetype PKCS12 -digestalg SHA-256 -sigalg SHA256withRSA "%JAR%" viet
jarsigner -verify -certs "%JAR%"
pause
