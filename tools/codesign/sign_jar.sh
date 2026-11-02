#!/bin/sh
# Ký file jar bằng chữ ký số "Việt".
# Cách dùng: sh tools/codesign/sign_jar.sh 20.jar /duong/dan/viet-codesign.p12 <mat_khau>
JAR=${1:-20.jar}
KS=${2:-viet-codesign.p12}
PW=$3
[ -z "$PW" ] && { printf "Mat khau keystore: "; stty -echo; read PW; stty echo; echo; }
jarsigner -J-Dfile.encoding=UTF-8 -keystore "$KS" -storetype PKCS12 -storepass "$PW" -digestalg SHA-256 -sigalg SHA256withRSA "$JAR" viet || exit 1
jarsigner -verify -certs "$JAR" | tail -3
