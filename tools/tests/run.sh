#!/bin/bash
# Chạy các bản tự kiểm tra: biên dịch server vào thư mục tạm rồi chạy từng file *Check.java.
set -e
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
find src -name '*.java' > "$out/srcs.txt"
javac -encoding UTF-8 -nowarn -proc:full -cp "lib/*" -d "$out" @"$out/srcs.txt" 2>/dev/null
for t in tools/tests/*Check.java; do
    javac -encoding UTF-8 -cp "$out:lib/*" -d "$out" "$t"
    java -cp "$out:lib/*" "$(basename "$t" .java)"
done
