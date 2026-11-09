@ECHO OFF
REM RAM_MAX: RAM tối đa cho server Java. Máy 2 GB: 448m (thực tế server chỉ dùng khoảng 150 - 300 MB, phần thừa tự trả lại máy).
REM Máy 4 GB trở lên có thể để 1g.
IF "%RAM_MAX%"=="" SET RAM_MAX=448m
java -server -Dfile.encoding=UTF-8 -Xms48m -Xmx%RAM_MAX% -XX:+UseG1GC -XX:G1PeriodicGCInterval=20000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=25 -XX:+UseStringDeduplication -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xss384k -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError -jar 20.jar
PAUSE
