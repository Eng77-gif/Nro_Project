#!/bin/sh
# Chạy server cho VPS Linux yếu (1-2 nhân, 1-2 GB RAM). Đổi RAM_MAX theo máy: 1 GB -> 320m, 2 GB -> 448m.
# Server thực tế chỉ dùng khoảng 150 - 300 MB; G1 tự trả RAM thừa cho máy khi rảnh (G1PeriodicGCInterval).
RAM_MAX=${RAM_MAX:-384m}
cd "$(dirname "$0")"
exec java -server -Dfile.encoding=UTF-8 -Xms48m -Xmx$RAM_MAX -XX:+UseG1GC -XX:G1PeriodicGCInterval=20000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=25 -XX:+UseStringDeduplication -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xss384k -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError -jar 20.jar
